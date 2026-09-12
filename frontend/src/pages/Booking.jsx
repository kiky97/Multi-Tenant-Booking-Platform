import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
    CalendarClock,
    CheckCircle2,
    Clock3,
    CreditCard,
    Mail,
    Phone,
    Scissors,
    ShieldCheck,
    Sparkles,
    UserRound,
    X
} from 'lucide-react';

import BookingStepper from '../components/booking/BookingStepper';
import StepNavigation from '../components/booking/StepNavigation';
import BookingCalendar from '../components/booking/BookingCalendar';
import TimeSlotList from '../components/booking/TimeSlotList';
import StripeHoldModal from '../components/booking/StripeHoldModal';
import LuxuryCard from '../components/ui/LuxuryCard';
import GoldButton from '../components/ui/GoldButton';
import SectionTitle from '../components/ui/SectionTitle';
import useBodyScrollLock from '../hooks/useBodyScrollLock';
import { publicApi, PublicApiError } from '../api/publicApi';
import { primeStripeOrigin } from '../utils/stripeHints';
import { scrollWindowToElement } from '../utils/scroll';
import { reportAppError } from '../utils/appErrorBus';
import { markHomeTopScrollPending } from '../utils/bookingSuccessNavigation';
import '../styles/booking-shared.css';
import '../styles/booking.css';

const FALLBACK_TREATMENT_IMAGE =
    'https://images.unsplash.com/photo-1622286342621-4bd786c2447c?auto=format&fit=crop&w=1200&q=80';
const FALLBACK_EMPLOYEE_IMAGE =
    'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?auto=format&fit=crop&w=1200&q=80';

const BOOKING_STEPS = [
    { id: 1, label: 'Service' },
    { id: 2, label: 'Barber' },
    { id: 3, label: 'Time' },
    { id: 4, label: 'Details' }
];
const HOLD_DURATION_SECONDS = 10 * 60;
const SLOT_AVAILABILITY_REFRESH_INTERVAL_MS = 5_000;
const BOOKING_CONFIRMATION_POLL_ATTEMPTS = 12;
const BOOKING_CONFIRMATION_POLL_DELAY_MS = 800;
const BOOKING_API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const HOLD_ACCESS_TOKEN_HEADER = 'X-Booking-Hold-Access-Token';

function toIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function formatLongDate(date) {
    return new Intl.DateTimeFormat('en-IE', {
        weekday: 'long',
        day: 'numeric',
        month: 'long'
    }).format(date);
}

function formatBookingDate(value) {
    if (!value) {
        return '--';
    }

    const [year, month, day] = String(value)
        .split('-')
        .map((part) => Number.parseInt(part, 10));

    if (!year || !month || !day) {
        return String(value);
    }

    return formatLongDate(new Date(year, month - 1, day));
}

function formatTime(value) {
    return value ? value.slice(0, 5) : '--';
}

function formatCurrency(value) {
    if (value == null || value === '') {
        return '--';
    }

    return new Intl.NumberFormat('en-IE', {
        style: 'currency',
        currency: 'EUR'
    }).format(Number(value));
}

function formatCountdown(totalSeconds) {
    if (totalSeconds == null) {
        return '--:--';
    }

    const safeSeconds = Math.max(0, totalSeconds);
    const minutes = String(Math.floor(safeSeconds / 60)).padStart(1, '0');
    const seconds = String(safeSeconds % 60).padStart(2, '0');
    return `${minutes}:${seconds}`;
}

function initialCustomerForm() {
    return {
        customerName: '',
        customerEmail: '',
        customerPhone: ''
    };
}

function wait(delayMs) {
    return new Promise((resolve) => {
        window.setTimeout(resolve, delayMs);
    });
}

export default function Booking() {
    const navigate = useNavigate();
    const activeSectionRef = useRef(null);
    const pendingSectionFocusRef = useRef(false);
    const activeHoldRef = useRef(null);
    const stepRef = useRef(1);
    const releasingHoldRef = useRef(false);
    const backgroundReleaseSentRef = useRef(false);
    const successDialogRef = useRef(null);
    const confirmationFlashTimeoutRef = useRef(null);

    const [step, setStep] = useState(1);

    const [catalogLoading, setCatalogLoading] = useState(true);
    const [catalogError, setCatalogError] = useState('');
    const [employees, setEmployees] = useState([]);
    const [treatments, setTreatments] = useState([]);

    const [selectedTreatmentId, setSelectedTreatmentId] = useState('');
    const [selectedEmployeeId, setSelectedEmployeeId] = useState('');
    const [selectedDate, setSelectedDate] = useState(() => new Date());

    const [slotsLoading, setSlotsLoading] = useState(false);
    const [slotsError, setSlotsError] = useState('');
    const [slots, setSlots] = useState([]);
    const [selectedSlot, setSelectedSlot] = useState(null);
    const [timeSelectionError, setTimeSelectionError] = useState('');
    const [holdingSlot, setHoldingSlot] = useState(false);
    const [activeHold, setActiveHold] = useState(null);
    const [holdSecondsLeft, setHoldSecondsLeft] = useState(null);
    const [releasingHold, setReleasingHold] = useState(false);

    const [customerForm, setCustomerForm] = useState(initialCustomerForm());
    const [fieldErrors, setFieldErrors] = useState({});
    const [submitError, setSubmitError] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [confirmationAccepted, setConfirmationAccepted] = useState(false);
    const [confirmationConsentFlash, setConfirmationConsentFlash] = useState(false);
    const [bookingResult, setBookingResult] = useState(null);
    const [checkoutConfig, setCheckoutConfig] = useState({
        currency: 'eur',
        stripePublishableKey: ''
    });
    const [checkoutConfigError, setCheckoutConfigError] = useState('');
    const [stripeModalOpen, setStripeModalOpen] = useState(false);
    useBodyScrollLock(Boolean(bookingResult));

    useEffect(() => {
        primeStripeOrigin();
    }, []);

    function getEmployeeTreatmentIds(employee) {
        if (!Array.isArray(employee?.treatmentIds)) {
            return [];
        }

        return employee.treatmentIds.filter((value) => typeof value === 'string' && value.trim());
    }

    const sortedTreatments = useMemo(
        () => [...treatments].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [treatments]
    );

    const sortedEmployees = useMemo(
        () => [...employees].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [employees]
    );

    const visibleTreatments = useMemo(() => {
        const supportedTreatmentIds = new Set(
            sortedEmployees.flatMap((employee) => getEmployeeTreatmentIds(employee))
        );

        return sortedTreatments.filter((treatment) => supportedTreatmentIds.has(treatment.id));
    }, [sortedEmployees, sortedTreatments]);

    const eligibleEmployees = useMemo(() => {
        if (!selectedTreatmentId) {
            return [];
        }

        return sortedEmployees.filter((employee) =>
            getEmployeeTreatmentIds(employee).includes(selectedTreatmentId)
        );
    }, [selectedTreatmentId, sortedEmployees]);

    const selectedTreatment = useMemo(
        () => sortedTreatments.find((item) => item.id === selectedTreatmentId) || null,
        [sortedTreatments, selectedTreatmentId]
    );

    const selectedEmployee = useMemo(
        () => sortedEmployees.find((item) => item.id === selectedEmployeeId) || null,
        [sortedEmployees, selectedEmployeeId]
    );
    const selectedTreatmentPrice = selectedTreatment?.price ?? null;

    const summarySlot = selectedSlot
        ? selectedSlot
        : bookingResult
          ? {
                startTime: bookingResult.startTime,
                endTime: bookingResult.endTime
            }
          : null;

    const maxReachedStep = activeHold ? 4 : selectedEmployeeId ? 3 : selectedTreatmentId ? 2 : 1;
    const inlineSubmitError = stripeModalOpen ? '' : submitError;

    function buildHeldBookingUrl(bookingId) {
        return new URL(
            `${BOOKING_API_BASE_URL}/api/v1/public/bookings/${bookingId}`,
            window.location.origin
        ).toString();
    }

    function focusBookingSectionOnNextRender() {
        pendingSectionFocusRef.current = true;
    }

    function focusBookingSection({ behavior = 'smooth' } = {}) {
        window.requestAnimationFrame(() => {
            scrollWindowToElement(activeSectionRef.current, {
                behavior,
                extraOffset: 24,
                hideScrollbar: true
            });
        });
    }

    useEffect(() => {
        activeHoldRef.current = activeHold;
        backgroundReleaseSentRef.current = false;
    }, [activeHold]);

    useEffect(() => {
        stepRef.current = step;
    }, [step]);

    useEffect(() => {
        releasingHoldRef.current = releasingHold;
    }, [releasingHold]);

    useEffect(() => {
        if (!bookingResult) {
            return undefined;
        }

        const animationFrameId = window.requestAnimationFrame(() => {
            successDialogRef.current?.focus();
        });

        return () => {
            window.cancelAnimationFrame(animationFrameId);
        };
    }, [bookingResult]);

    useEffect(
        () => () => {
            if (confirmationFlashTimeoutRef.current) {
                window.clearTimeout(confirmationFlashTimeoutRef.current);
            }
        },
        []
    );

    useEffect(() => {
        let ignore = false;

        async function loadCatalog() {
            setCatalogLoading(true);
            setCatalogError('');

            try {
                const [employeeData, treatmentData] = await Promise.all([
                    publicApi.getEmployees({ bookable: true }),
                    publicApi.getTreatments()
                ]);

                if (ignore) return;

                const nextEmployees = Array.isArray(employeeData) ? employeeData : [];
                const nextTreatments = Array.isArray(treatmentData) ? treatmentData : [];

                setEmployees(nextEmployees);
                setTreatments(nextTreatments);
            } catch (error) {
                if (!ignore) {
                    setCatalogError(error.message || 'Failed to load booking catalog.');
                }
            } finally {
                if (!ignore) {
                    setCatalogLoading(false);
                }
            }
        }

        loadCatalog();

        return () => {
            ignore = true;
        };
    }, []);

    useEffect(() => {
        setSelectedTreatmentId((prev) =>
            visibleTreatments.some((treatment) => treatment.id === prev)
                ? prev
                : visibleTreatments[0]?.id || ''
        );
    }, [visibleTreatments]);

    useEffect(() => {
        setSelectedEmployeeId((prev) =>
            eligibleEmployees.some((employee) => employee.id === prev)
                ? prev
                : eligibleEmployees[0]?.id || ''
        );
    }, [eligibleEmployees]);

    useEffect(() => {
        setSelectedSlot(null);
        setTimeSelectionError('');
        setSubmitError('');
    }, [selectedTreatmentId, selectedEmployeeId]);

    useEffect(() => {
        let ignore = false;

        async function loadCheckoutConfig() {
            setCheckoutConfigError('');

            try {
                const data = await publicApi.getBookingCheckoutConfig();

                if (ignore) return;

                setCheckoutConfig({
                    currency: data?.currency || 'eur',
                    stripePublishableKey: data?.stripePublishableKey || ''
                });
            } catch (error) {
                if (!ignore) {
                    setCheckoutConfigError(
                        error.message || 'Failed to load Stripe checkout settings.'
                    );
                }
            }
        }

        loadCheckoutConfig();

        return () => {
            ignore = true;
        };
    }, []);

    useEffect(() => {
        let ignore = false;

        async function loadAvailability() {
            if (!selectedEmployeeId || !selectedTreatmentId) {
                setSlots([]);
                setSelectedSlot(null);
                setTimeSelectionError('');
                return;
            }

            setSlotsLoading(true);
            setSlotsError('');
            setTimeSelectionError('');

            try {
                const data = await publicApi.getAvailability({
                    employeeId: selectedEmployeeId,
                    treatmentId: selectedTreatmentId,
                    date: toIsoDate(selectedDate)
                });

                if (ignore) return;

                const nextSlots = Array.isArray(data) ? data : [];
                setSlots(nextSlots);
                setSelectedSlot((current) =>
                    current &&
                    nextSlots.some((slot) => slot.startTime === current.startTime && slot.available)
                        ? current
                        : null
                );
            } catch (error) {
                if (!ignore) {
                    setSlots([]);
                    setSelectedSlot(null);
                    setSlotsError(error.message || 'Failed to load available time slots.');
                }
            } finally {
                if (!ignore) {
                    setSlotsLoading(false);
                }
            }
        }

        loadAvailability();

        return () => {
            ignore = true;
        };
    }, [selectedEmployeeId, selectedTreatmentId, selectedDate]);

    useEffect(() => {
        if (!activeHold?.expiresAtMs) {
            setHoldSecondsLeft(null);
            return undefined;
        }

        function updateCountdown() {
            const nextValue = Math.max(0, Math.floor((activeHold.expiresAtMs - Date.now()) / 1000));
            setHoldSecondsLeft(nextValue);
        }

        updateCountdown();
        const intervalId = window.setInterval(updateCountdown, 1000);

        return () => {
            window.clearInterval(intervalId);
        };
    }, [activeHold]);

    useEffect(() => {
        if (!activeHold || holdSecondsLeft !== 0) {
            return undefined;
        }

        let cancelled = false;

        async function expireHold() {
            await releaseHeldSlot();

            if (cancelled) {
                return;
            }

            setStep(3);
            setSubmitError('');
            setSelectedSlot(null);
            setTimeSelectionError(
                'This appointment hold has expired. Please choose another time slot.'
            );
            focusBookingSectionOnNextRender();
        }

        void expireHold();

        return () => {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps -- Hold expiry should release once for the active hold; adding releaseHeldSlot can duplicate release attempts.
    }, [activeHold, holdSecondsLeft]);

    useEffect(() => {
        if (!timeSelectionError) {
            return undefined;
        }

        const timeoutId = window.setTimeout(() => {
            setTimeSelectionError('');
        }, 5000);

        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [timeSelectionError]);

    useEffect(() => {
        if (step !== 3) {
            setTimeSelectionError('');
        }
    }, [step]);

    useEffect(() => {
        if (!pendingSectionFocusRef.current) {
            return;
        }

        pendingSectionFocusRef.current = false;
        focusBookingSection();
    }, [step]);

    useEffect(() => {
        if (step !== 3 || !selectedEmployeeId || !selectedTreatmentId) {
            return;
        }

        setSlotsLoading(true);
        setSlotsError('');

        refreshAvailabilityAfterBooking({ keepCurrentSelection: true })
            .catch((error) => {
                setSlotsError(error.message || 'Failed to refresh time slots.');
            })
            .finally(() => {
                setSlotsLoading(false);
            });
        // eslint-disable-next-line react-hooks/exhaustive-deps -- This refresh is scoped to entering the slot step; adding booking inputs changes when API refreshes fire.
    }, [step]);

    useEffect(() => {
        if (
            step !== 3 ||
            !selectedEmployeeId ||
            !selectedTreatmentId ||
            Boolean(activeHold?.id) ||
            holdingSlot ||
            releasingHold ||
            slotsLoading
        ) {
            return undefined;
        }

        const intervalId = window.setInterval(() => {
            if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
                return;
            }

            const selectedSlotSnapshot = selectedSlot;

            refreshAvailabilityAfterBooking({
                keepCurrentSelection: true,
                selectionToKeep: selectedSlotSnapshot
            })
                .then((nextSlots) => {
                    setSlotsError('');

                    if (
                        selectedSlotSnapshot &&
                        !nextSlots.some(
                            (slot) =>
                                slot.startTime === selectedSlotSnapshot.startTime &&
                                slot.endTime === selectedSlotSnapshot.endTime &&
                                slot.available
                        )
                    ) {
                        setTimeSelectionError(
                            'This time slot is no longer available. Please choose another time.'
                        );
                    }
                })
                .catch((error) => {
                    setSlotsError(error.message || 'Failed to refresh time slots.');
                });
        }, SLOT_AVAILABILITY_REFRESH_INTERVAL_MS);

        return () => {
            window.clearInterval(intervalId);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps -- Keep polling tied to slot/hold state; adding refreshAvailabilityAfterBooking can churn the interval.
    }, [
        activeHold?.id,
        holdingSlot,
        releasingHold,
        selectedDate,
        selectedEmployeeId,
        selectedSlot,
        selectedTreatmentId,
        slotsLoading,
        step
    ]);

    useEffect(() => {
        function releaseHeldSlotOnPageLeave() {
            const holdId = activeHoldRef.current?.id;
            const holdAccessToken = activeHoldRef.current?.holdAccessToken;

            if (
                stepRef.current !== 4 ||
                !holdId ||
                !holdAccessToken ||
                releasingHoldRef.current ||
                backgroundReleaseSentRef.current
            ) {
                return;
            }

            backgroundReleaseSentRef.current = true;

            fetch(buildHeldBookingUrl(holdId), {
                method: 'DELETE',
                headers: {
                    Accept: 'application/json',
                    [HOLD_ACCESS_TOKEN_HEADER]: holdAccessToken
                },
                keepalive: true
            }).catch((error) => {
                reportAppError(error, {
                    source: 'booking-release-page-leave',
                    level: 'warn',
                    message: 'The held booking slot could not be released while leaving the page.',
                    notify: false
                });
            });
        }

        window.addEventListener('pagehide', releaseHeldSlotOnPageLeave);
        window.addEventListener('beforeunload', releaseHeldSlotOnPageLeave);

        return () => {
            releaseHeldSlotOnPageLeave();
            window.removeEventListener('pagehide', releaseHeldSlotOnPageLeave);
            window.removeEventListener('beforeunload', releaseHeldSlotOnPageLeave);
        };
    }, []);

    function updateCustomerField(field, value) {
        setCustomerForm((prev) => ({ ...prev, [field]: value }));
        setFieldErrors((prev) => ({
            ...prev,
            'customer.name': field === 'customerName' ? '' : prev['customer.name'],
            'customer.email': field === 'customerEmail' ? '' : prev['customer.email'],
            'customer.phone': field === 'customerPhone' ? '' : prev['customer.phone']
        }));
        setSubmitError('');
    }

    async function refreshAvailabilityAfterBooking({
        keepCurrentSelection = false,
        selectionToKeep = selectedSlot
    } = {}) {
        if (!selectedEmployeeId || !selectedTreatmentId) {
            setSlots([]);
            return [];
        }

        const refreshedSlots = await publicApi.getAvailability({
            employeeId: selectedEmployeeId,
            treatmentId: selectedTreatmentId,
            date: toIsoDate(selectedDate)
        });

        const nextSlots = Array.isArray(refreshedSlots) ? refreshedSlots : [];
        setSlots(nextSlots);
        setSelectedSlot((current) =>
            keepCurrentSelection && (selectionToKeep || current)
                ? nextSlots.find(
                      (slot) =>
                          slot.startTime === (selectionToKeep || current).startTime &&
                          slot.endTime === (selectionToKeep || current).endTime &&
                          slot.available
                  ) || null
                : null
        );
        return nextSlots;
    }

    async function releaseHeldSlot({ keepSelection = false, selectionToKeep = selectedSlot } = {}) {
        const holdId = activeHoldRef.current?.id;
        const holdAccessToken = activeHoldRef.current?.holdAccessToken;

        if (!holdId || !holdAccessToken || releasingHoldRef.current) {
            return;
        }

        backgroundReleaseSentRef.current = true;
        setReleasingHold(true);

        try {
            await publicApi.cancelBooking(holdId, holdAccessToken);
        } catch (error) {
            setSlotsError(error.message || 'Failed to release the held slot.');
        } finally {
            activeHoldRef.current = null;
            setActiveHold(null);
            setHoldSecondsLeft(null);
            setStripeModalOpen(false);
            setSubmitError('');
            setFieldErrors({});
            if (!keepSelection) {
                setSelectedSlot(null);
            }

            try {
                await refreshAvailabilityAfterBooking({
                    keepCurrentSelection: keepSelection,
                    selectionToKeep: keepSelection ? selectionToKeep : null
                });
            } catch (refreshError) {
                setSlotsError(
                    refreshError.message || 'Failed to refresh time slots after releasing the hold.'
                );
            }

            setReleasingHold(false);
        }
    }

    async function nextStep() {
        if (releasingHold || submitting || bookingResult) {
            return;
        }

        if (step === 1 && !selectedTreatmentId) return;
        if (step === 2 && !selectedEmployeeId) return;

        if (step === 3) {
            if (!selectedTreatment || !selectedEmployee || !selectedSlot || holdingSlot) {
                return;
            }

            setHoldingSlot(true);
            setSlotsError('');
            setTimeSelectionError('');
            setSubmitError('');

            try {
                const response = await publicApi.holdBookingSlot({
                    employeeId: selectedEmployee.id,
                    treatmentId: selectedTreatment.id,
                    bookingDate: toIsoDate(selectedDate),
                    startTime: selectedSlot.startTime,
                    endTime: selectedSlot.endTime
                });

                const expiresAtMs = response?.expiresAt
                    ? new Date(response.expiresAt).getTime()
                    : Date.now() + HOLD_DURATION_SECONDS * 1000;

                if (!response?.id || !response?.holdAccessToken) {
                    throw new Error('Failed to hold the selected time slot.');
                }

                activeHoldRef.current = {
                    id: response.id,
                    expiresAtMs,
                    holdAccessToken: response.holdAccessToken
                };
                setActiveHold({
                    id: response.id,
                    expiresAtMs,
                    holdAccessToken: response.holdAccessToken
                });
                setHoldSecondsLeft(Math.max(0, Math.floor((expiresAtMs - Date.now()) / 1000)));
                setStep(4);
                focusBookingSectionOnNextRender();
            } catch (error) {
                const message =
                    error instanceof PublicApiError
                        ? error.message
                        : error.message || 'Failed to hold the selected time slot.';
                const isSlotConflict =
                    message.includes('already been booked') ||
                    message.includes('held by another guest');

                setTimeSelectionError(message);
                setSubmitError('');

                if (isSlotConflict) {
                    setSelectedSlot(null);

                    try {
                        await refreshAvailabilityAfterBooking();
                    } catch (refreshError) {
                        setSlotsError(
                            refreshError.message ||
                                'Failed to refresh time slots after hold conflict.'
                        );
                    }
                }
            } finally {
                setHoldingSlot(false);
            }

            return;
        }

        setStep((prev) => Math.min(prev + 1, 4));
        focusBookingSectionOnNextRender();
    }

    async function prevStep() {
        if (releasingHold || holdingSlot || submitting || bookingResult) {
            return;
        }

        if (step === 4 && activeHold) {
            await releaseHeldSlot({
                keepSelection: true,
                selectionToKeep: selectedSlot
            });
            setStep(3);
            focusBookingSectionOnNextRender();
            return;
        }

        setStep((prev) => Math.max(prev - 1, 1));
        focusBookingSectionOnNextRender();
    }

    async function handleStepChange(targetStep) {
        if (
            releasingHold ||
            holdingSlot ||
            submitting ||
            bookingResult ||
            targetStep > step ||
            targetStep > maxReachedStep ||
            targetStep === step
        ) {
            return;
        }

        if (step === 4 && activeHold && targetStep < 4) {
            await releaseHeldSlot({
                keepSelection: true,
                selectionToKeep: selectedSlot
            });
        }

        setStep(targetStep);
        focusBookingSectionOnNextRender();
    }

    function handleSlotSelection(slot) {
        if (!slot?.available) {
            return;
        }

        setSlotsError('');
        setTimeSelectionError('');
        setSubmitError('');
        setSelectedSlot(slot);
        setBookingResult(null);
    }

    function isRecoverableHoldError(message) {
        return Boolean(
            message &&
            (message.includes('already been booked') ||
                message.includes('held by another guest') ||
                message.includes('hold has expired') ||
                message.includes('already been confirmed') ||
                message.includes('already been cancelled') ||
                message.includes('no longer available'))
        );
    }

    async function authorizeStripeHold(confirmationTokenId) {
        if (!activeHold?.id || !activeHold?.holdAccessToken) {
            throw new Error(
                'This appointment hold is no longer available. Please choose another time.'
            );
        }

        setSubmitError('');
        setFieldErrors({});

        try {
            const response = await publicApi.prepareHeldBookingCheckout(
                activeHold.id,
                {
                    customer: {
                        name: customerForm.customerName.trim(),
                        email: customerForm.customerEmail.trim(),
                        phone: customerForm.customerPhone.trim() || null
                    },
                    confirmationTokenId
                },
                activeHold.holdAccessToken
            );

            return response;
        } catch (error) {
            if (error instanceof PublicApiError) {
                setFieldErrors(error.fieldErrors || {});
                setSubmitError(error.message);
            } else {
                setSubmitError(
                    error.message || 'Failed to start the Stripe payment. Please try again.'
                );
            }

            throw error;
        }
    }

    async function handleSubmit(event) {
        event.preventDefault();

        if (
            !selectedTreatment ||
            !selectedEmployee ||
            !selectedSlot ||
            !activeHold?.id ||
            !activeHold?.holdAccessToken
        ) {
            setSubmitError(
                'Please hold a service, barber, and time slot before confirming the booking.'
            );
            return;
        }

        if (!confirmationAccepted) {
            if (confirmationFlashTimeoutRef.current) {
                window.clearTimeout(confirmationFlashTimeoutRef.current);
            }

            setConfirmationConsentFlash(false);

            window.requestAnimationFrame(() => {
                setConfirmationConsentFlash(true);
            });

            confirmationFlashTimeoutRef.current = window.setTimeout(() => {
                setConfirmationConsentFlash(false);
                confirmationFlashTimeoutRef.current = null;
            }, 1400);
            return;
        }

        setSubmitError('');
        setFieldErrors({});

        if (selectedTreatmentPrice == null) {
            setSubmitError(
                'The selected service price is unavailable. Please choose your treatment again.'
            );
            return;
        }

        if (!checkoutConfig.stripePublishableKey) {
            setSubmitError(
                'Stripe checkout is not configured yet. Please add a publishable key first.'
            );
            return;
        }

        setSubmitting(true);

        try {
            await publicApi.validateHeldBookingCheckout(
                activeHold.id,
                {
                    customer: {
                        name: customerForm.customerName.trim(),
                        email: customerForm.customerEmail.trim(),
                        phone: customerForm.customerPhone.trim() || null
                    }
                },
                activeHold.holdAccessToken
            );

            setStripeModalOpen(true);
        } catch (error) {
            if (error instanceof PublicApiError) {
                setFieldErrors(error.fieldErrors || {});
                setSubmitError(error.message);

                if (isRecoverableHoldError(error.message)) {
                    setStripeModalOpen(false);
                    activeHoldRef.current = null;
                    setActiveHold(null);
                    setHoldSecondsLeft(null);
                    setStep(3);
                    setTimeSelectionError(error.message);
                    setSelectedSlot(null);
                    focusBookingSectionOnNextRender();

                    try {
                        await refreshAvailabilityAfterBooking();
                    } catch (refreshError) {
                        setSlotsError(
                            refreshError.message ||
                                'Failed to refresh time slots after booking conflict.'
                        );
                    }
                }
            } else {
                setSubmitError(error.message || 'Booking validation failed. Please try again.');
            }
        } finally {
            setSubmitting(false);
        }
    }

    async function handleStripeConfirmation(paymentIntentId) {
        if (
            !selectedTreatment ||
            !selectedEmployee ||
            !selectedSlot ||
            !activeHold?.id ||
            !activeHold?.holdAccessToken
        ) {
            setStripeModalOpen(false);
            setSubmitError(
                'Please hold a service, barber, and time slot before confirming the booking.'
            );
            return;
        }

        setSubmitting(true);
        setSubmitError('');
        setFieldErrors({});

        try {
            const response = await publicApi.confirmHeldBooking(
                activeHold.id,
                {
                    paymentIntentId
                },
                activeHold.holdAccessToken
            );
            const finalizedBooking = await waitForSettledBookingStatus(
                response,
                activeHold.holdAccessToken
            );

            setStripeModalOpen(false);
            activeHoldRef.current = null;
            setActiveHold(null);
            setHoldSecondsLeft(null);
            setBookingResult(finalizedBooking);
            setTimeSelectionError('');
            await refreshAvailabilityAfterBooking();
        } catch (error) {
            if (error instanceof PublicApiError) {
                setFieldErrors(error.fieldErrors || {});
                setSubmitError(error.message);

                if (
                    error.message?.includes('already been booked') ||
                    error.message?.includes('held by another guest') ||
                    error.message?.includes('hold has expired')
                ) {
                    setStripeModalOpen(false);
                    activeHoldRef.current = null;
                    setActiveHold(null);
                    setHoldSecondsLeft(null);
                    setStep(3);
                    setTimeSelectionError(error.message);
                    setSelectedSlot(null);

                    try {
                        await refreshAvailabilityAfterBooking();
                    } catch (refreshError) {
                        setSlotsError(
                            refreshError.message ||
                                'Failed to refresh time slots after booking conflict.'
                        );
                    }
                }
            } else {
                setSubmitError(error.message || 'Booking failed. Please try again.');
            }
        } finally {
            setSubmitting(false);
        }
    }

    async function waitForSettledBookingStatus(initialBooking, holdAccessToken) {
        let latestBooking = initialBooking;

        if (!initialBooking?.id || initialBooking.status === 'CONFIRMED') {
            return initialBooking;
        }

        try {
            for (let attempt = 0; attempt < BOOKING_CONFIRMATION_POLL_ATTEMPTS; attempt += 1) {
                await wait(BOOKING_CONFIRMATION_POLL_DELAY_MS);
                latestBooking = await publicApi.getBooking(initialBooking.id, holdAccessToken);

                if (latestBooking?.status === 'CONFIRMED') {
                    return latestBooking;
                }

                if (latestBooking?.status && latestBooking.status !== 'PENDING') {
                    return latestBooking;
                }
            }
        } catch (error) {
            reportAppError(error, {
                source: 'booking-status-poll',
                level: 'warn',
                message:
                    'Payment completed, but the latest booking status could not be refreshed automatically.',
                notify: false
            });
            return latestBooking;
        }

        return latestBooking;
    }

    function handleSuccessClose() {
        setBookingResult(null);
        setStep(1);
        setSelectedSlot(null);
        setSubmitError('');
        setTimeSelectionError('');
        setSlotsError('');
        setFieldErrors({});
        setCustomerForm(initialCustomerForm());
        setConfirmationAccepted(false);
        setConfirmationConsentFlash(false);

        markHomeTopScrollPending();
        navigate('/', { replace: true });
    }

    function renderAppointmentSummaryCard() {
        return (
            <LuxuryCard className="booking-summary-card">
                <div className="booking-summary-badge">Appointment Summary</div>
                <h2>{selectedTreatment?.name || 'Choose a service'}</h2>
                <p className="booking-summary-description">
                    {selectedTreatment?.description ||
                        'Select a treatment to see the full summary and reservation details.'}
                </p>

                <div className="ornament !mt-0 !w-full" />

                <div className="booking-summary-list">
                    <div>
                        <span>
                            <Scissors size={14} /> Service
                        </span>
                        <strong>{selectedTreatment?.name || '--'}</strong>
                    </div>
                    <div>
                        <span>
                            <UserRound size={14} /> Barber
                        </span>
                        <strong>{selectedEmployee?.name || '--'}</strong>
                    </div>
                    <div>
                        <span>
                            <CalendarClock size={14} /> Date
                        </span>
                        <strong>{formatLongDate(selectedDate)}</strong>
                    </div>
                    <div>
                        <span>
                            <Clock3 size={14} /> Time
                        </span>
                        <strong>
                            {summarySlot
                                ? `${formatTime(summarySlot.startTime)} - ${formatTime(summarySlot.endTime)}`
                                : '--'}
                        </strong>
                    </div>
                    <div>
                        <span>
                            <Clock3 size={14} /> Duration
                        </span>
                        <strong>
                            {selectedTreatment ? `${selectedTreatment.durationMinutes} min` : '--'}
                        </strong>
                    </div>
                    <div>
                        <span>
                            <CreditCard size={14} /> Price
                        </span>
                        <strong>
                            {selectedTreatment ? formatCurrency(selectedTreatment.price) : '--'}
                        </strong>
                    </div>
                </div>
            </LuxuryCard>
        );
    }

    function renderBeforeConfirmCard() {
        return (
            <LuxuryCard className="booking-summary-card">
                <div className="booking-summary-badge">Before You Confirm</div>
                <ul className="booking-note-list">
                    <li>
                        By confirming this booking and completing the payment, you confirm all
                        details in the Appointment Summary, agree to an immediate Stripe payment of{' '}
                        {formatCurrency(selectedTreatmentPrice)}, and reserve your selected date and
                        time slot.
                    </li>
                    <li>In case of a no-show, the payment is non-refundable.</li>
                    <li>
                        If you are not sure you can attend at the selected date and time, we
                        recommend booking your slot via a direct phone call to the barbershop.
                    </li>
                    <li>
                        Stripe opens on confirmation and charges the full selected service price
                        immediately.
                    </li>
                </ul>

                <label
                    className={`booking-confirm-consent ${confirmationConsentFlash ? 'booking-confirm-consent-flash' : ''}`}
                >
                    <input
                        type="checkbox"
                        checked={confirmationAccepted}
                        onChange={(event) => {
                            setConfirmationAccepted(event.target.checked);

                            if (event.target.checked) {
                                setConfirmationConsentFlash(false);
                            }
                        }}
                    />

                    <div className="booking-confirm-consent-copy">
                        <span className="booking-confirm-consent-text">
                            I have read all the information above and agree with everything stated.
                        </span>
                        <span className="booking-confirm-consent-hint">
                            Tick this box to enable booking confirmation.
                        </span>
                    </div>
                </label>
            </LuxuryCard>
        );
    }

    function renderSummaryCards() {
        return (
            <>
                {renderAppointmentSummaryCard()}
                {renderBeforeConfirmCard()}
            </>
        );
    }

    return (
        <div className="booking-page-shell">
            <motion.section
                initial={{ opacity: 0, y: 18 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.45 }}
                className="booking-hero-panel"
            >
                <div className="booking-hero-copy">
                    <h1 className="booking-title">Royal Chair Booking</h1>
                    <div className="booking-guide" aria-label="How booking works">
                        <p className="booking-guide-paragraph">
                            Booking your appointment is quick and easy &mdash; just follow a few
                            simple steps and you&rsquo;re all set.
                        </p>
                        <p className="booking-guide-paragraph">
                            We&rsquo;ll guide you through everything, from choosing your service to
                            confirming your booking. You can use the Next and Back buttons below, or
                            jump between steps using the bar at the top if you need to change
                            anything.
                        </p>
                        <p className="booking-guide-heading">Here&rsquo;s how it works:</p>
                        <ul className="booking-guide-list">
                            <li>Pick the service you&rsquo;re after</li>
                            <li>Choose your barber</li>
                            <li>Select a date and a time that suits you</li>
                            <li>Enter your details, review your booking, and confirm</li>
                        </ul>
                        <p className="booking-guide-note">
                            Once you&rsquo;ve selected a time, we&rsquo;ll hold it for 10 minutes so
                            you can finish your booking without rushing.
                        </p>
                    </div>

                    <div className="booking-feature-strip">
                        <div className="booking-feature-chip">
                            <Sparkles size={16} />
                            <span>Premium service selection</span>
                        </div>
                        <div className="booking-feature-chip">
                            <CalendarClock size={16} />
                            <span>Live availability by date</span>
                        </div>
                        <div className="booking-feature-chip">
                            <ShieldCheck size={16} />
                            <span>Secure payment flow</span>
                        </div>
                    </div>
                </div>
            </motion.section>

            <div className="booking-stepper-shell">
                <BookingStepper
                    currentStep={step}
                    steps={BOOKING_STEPS}
                    onStepChange={handleStepChange}
                    maxReachedStep={maxReachedStep}
                />
            </div>

            {catalogError ? (
                <div className="booking-status-card booking-status-card-error">{catalogError}</div>
            ) : null}
            {checkoutConfigError ? (
                <div className="booking-status-card booking-status-card-error">
                    {checkoutConfigError}
                </div>
            ) : null}

            <div className="booking-page-grid">
                <div className="booking-main-column">
                    {step === 1 ? (
                        <motion.section
                            ref={activeSectionRef}
                            key="step-1"
                            initial={{ opacity: 0, y: 18 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.25 }}
                            className="booking-section-stack"
                        >
                            <SectionTitle title="Choose Your Service" subtitle="Step 1" />
                            {catalogLoading ? (
                                <LuxuryCard className="text-center text-smoke">
                                    Loading services...
                                </LuxuryCard>
                            ) : null}
                            {!catalogLoading && visibleTreatments.length === 0 ? (
                                <LuxuryCard className="text-center text-smoke">
                                    No services are currently available for online booking.
                                </LuxuryCard>
                            ) : null}
                            {!catalogLoading && visibleTreatments.length > 0 ? (
                                <div className="booking-card-grid">
                                    {visibleTreatments.map((treatment, index) => {
                                        const isSelected = selectedTreatmentId === treatment.id;

                                        return (
                                            <button
                                                key={treatment.id}
                                                type="button"
                                                className={`booking-choice-card ${isSelected ? 'booking-choice-card-active' : ''}`}
                                                onClick={() => {
                                                    setSelectedTreatmentId(treatment.id);
                                                    setBookingResult(null);
                                                }}
                                            >
                                                <div className="booking-choice-media">
                                                    <img
                                                        src={
                                                            treatment.photoUrl ||
                                                            FALLBACK_TREATMENT_IMAGE
                                                        }
                                                        alt={
                                                            treatment.name
                                                                ? `${treatment.name} service`
                                                                : 'Booking service'
                                                        }
                                                        loading={index < 2 ? 'eager' : 'lazy'}
                                                        decoding="async"
                                                        fetchPriority={
                                                            index === 0 ? 'high' : 'auto'
                                                        }
                                                    />
                                                    <div
                                                        className="booking-choice-media-overlay"
                                                        aria-hidden="true"
                                                        style={{
                                                            background:
                                                                'linear-gradient(180deg, rgba(12, 9, 7, 0.1), rgba(12, 9, 7, 0.7))'
                                                        }}
                                                    />
                                                    {isSelected ? (
                                                        <span className="booking-choice-selected-badge">
                                                            Selected
                                                        </span>
                                                    ) : null}
                                                </div>
                                                <div className="booking-choice-body">
                                                    <h3>{treatment.name}</h3>
                                                    <p>{treatment.description}</p>
                                                    <div className="booking-choice-meta">
                                                        <span>{treatment.durationMinutes} min</span>
                                                        <span className="text-ivory">
                                                            {formatCurrency(treatment.price)}
                                                        </span>
                                                    </div>
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            ) : null}

                            <StepNavigation
                                step={step}
                                onNext={nextStep}
                                nextDisabled={!selectedTreatmentId}
                            />
                        </motion.section>
                    ) : null}

                    {step === 2 ? (
                        <motion.section
                            ref={activeSectionRef}
                            key="step-2"
                            initial={{ opacity: 0, y: 18 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.25 }}
                            className="booking-section-stack"
                        >
                            <SectionTitle title="Pick Your Barber" subtitle="Step 2" />
                            {catalogLoading ? (
                                <LuxuryCard className="text-center text-smoke">
                                    Loading barbers...
                                </LuxuryCard>
                            ) : null}
                            {!catalogLoading && eligibleEmployees.length === 0 ? (
                                <LuxuryCard className="text-center text-smoke">
                                    No barbers currently provide the selected service.
                                </LuxuryCard>
                            ) : null}
                            {!catalogLoading && eligibleEmployees.length > 0 ? (
                                <div className="booking-card-grid booking-card-grid-employees">
                                    {eligibleEmployees.map((employee, index) => {
                                        const isSelected = selectedEmployeeId === employee.id;

                                        return (
                                            <button
                                                key={employee.id}
                                                type="button"
                                                className={`booking-choice-card ${isSelected ? 'booking-choice-card-active' : ''}`}
                                                onClick={() => {
                                                    setSelectedEmployeeId(employee.id);
                                                    setBookingResult(null);
                                                }}
                                            >
                                                <div className="booking-choice-media booking-choice-media-employee">
                                                    <img
                                                        src={
                                                            employee.photoUrl ||
                                                            FALLBACK_EMPLOYEE_IMAGE
                                                        }
                                                        alt={
                                                            employee.name
                                                                ? `${employee.name}${employee.role ? `, ${employee.role}` : ''}`
                                                                : 'Booking team member'
                                                        }
                                                        loading={index < 2 ? 'eager' : 'lazy'}
                                                        decoding="async"
                                                        fetchPriority={
                                                            index === 0 ? 'high' : 'auto'
                                                        }
                                                    />
                                                    <div
                                                        className="booking-choice-media-overlay"
                                                        aria-hidden="true"
                                                        style={{
                                                            background:
                                                                'linear-gradient(180deg, rgba(12, 9, 7, 0.15), rgba(12, 9, 7, 0.72))'
                                                        }}
                                                    />
                                                    {isSelected ? (
                                                        <span className="booking-choice-selected-badge">
                                                            Selected
                                                        </span>
                                                    ) : null}
                                                </div>
                                                <div className="booking-choice-body">
                                                    <h3>{employee.name}</h3>
                                                    <div className="text-[11px] uppercase tracking-[0.18em] text-goldBright">
                                                        {employee.role || 'Senior Barber'}
                                                    </div>
                                                    <p>
                                                        {employee.bio ||
                                                            'Sharp hands, calm energy, and premium attention to detail.'}
                                                    </p>
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            ) : null}

                            <StepNavigation
                                step={step}
                                onBack={prevStep}
                                onNext={nextStep}
                                nextDisabled={!selectedEmployeeId}
                            />
                        </motion.section>
                    ) : null}

                    {step === 3 ? (
                        <motion.section
                            ref={activeSectionRef}
                            key="step-3"
                            initial={{ opacity: 0, y: 18 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.25 }}
                            className="booking-section-stack"
                        >
                            <SectionTitle title="Select Date And Time" subtitle="Step 3" />
                            <div className="booking-datetime-grid">
                                <div className="booking-date-panel">
                                    <BookingCalendar
                                        selectedDate={selectedDate}
                                        onSelect={setSelectedDate}
                                    />
                                </div>
                                <div className="space-y-4 booking-time-panel">
                                    <TimeSlotList
                                        slots={slots}
                                        selectedSlot={selectedSlot}
                                        onSelect={handleSlotSelection}
                                        loading={slotsLoading}
                                        emptyMessage="No free slots for the selected service, barber, and date."
                                    />
                                    {slotsError ? (
                                        <div className="booking-status-card booking-status-card-error">
                                            {slotsError}
                                        </div>
                                    ) : null}
                                    {timeSelectionError ? (
                                        <div className="booking-status-card booking-status-card-error">
                                            {timeSelectionError}
                                        </div>
                                    ) : null}
                                </div>
                            </div>

                            <StepNavigation
                                step={step}
                                onBack={prevStep}
                                onNext={nextStep}
                                nextDisabled={!selectedSlot || holdingSlot}
                            />
                        </motion.section>
                    ) : null}

                    {step === 4 ? (
                        <motion.section
                            ref={activeSectionRef}
                            key="step-4"
                            initial={{ opacity: 0, y: 18 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.25 }}
                            className="booking-section-stack booking-section-stack-step-4"
                        >
                            <SectionTitle title="Your Details" subtitle="Step 4" />
                            {activeHold ? (
                                <div className="booking-hold-banner booking-hold-banner-centered">
                                    <ShieldCheck size={16} />
                                    <span>
                                        Appointment held for {formatCountdown(holdSecondsLeft)}
                                    </span>
                                </div>
                            ) : null}
                            <div className="booking-details-grid">
                                <form className="booking-details-main" onSubmit={handleSubmit}>
                                    <div className="booking-form-panel">
                                        <div className="booking-form-grid">
                                            <label className="booking-form-field">
                                                <span>
                                                    <UserRound size={15} />
                                                    Full name
                                                </span>
                                                <input
                                                    className="payment-input"
                                                    value={customerForm.customerName}
                                                    onChange={(event) =>
                                                        updateCustomerField(
                                                            'customerName',
                                                            event.target.value
                                                        )
                                                    }
                                                    placeholder="John Doe"
                                                    required
                                                />
                                                {fieldErrors['customer.name'] ? (
                                                    <small>{fieldErrors['customer.name']}</small>
                                                ) : null}
                                            </label>

                                            <label className="booking-form-field">
                                                <span>
                                                    <Mail size={15} />
                                                    Email
                                                </span>
                                                <input
                                                    className="payment-input"
                                                    type="email"
                                                    value={customerForm.customerEmail}
                                                    onChange={(event) =>
                                                        updateCustomerField(
                                                            'customerEmail',
                                                            event.target.value
                                                        )
                                                    }
                                                    placeholder="john@example.com"
                                                    required
                                                />
                                                {fieldErrors['customer.email'] ? (
                                                    <small>{fieldErrors['customer.email']}</small>
                                                ) : null}
                                            </label>

                                            <label className="booking-form-field">
                                                <span className="text-white">
                                                    <Phone size={15} />
                                                    Phone
                                                </span>
                                                <input
                                                    className="payment-input"
                                                    value={customerForm.customerPhone}
                                                    onChange={(event) =>
                                                        updateCustomerField(
                                                            'customerPhone',
                                                            event.target.value
                                                        )
                                                    }
                                                    placeholder="+353 87 000 0000"
                                                />
                                                {fieldErrors['customer.phone'] ? (
                                                    <small>{fieldErrors['customer.phone']}</small>
                                                ) : null}
                                            </label>
                                        </div>

                                        <div className="booking-inline-note">
                                            <ShieldCheck size={16} />
                                            <span>
                                                The personal details provided above, including your
                                                full name, phone number, and email address, are used
                                                for payment processing and for contacting you in
                                                case of any urgent matters related to your booking.
                                            </span>
                                        </div>

                                        {inlineSubmitError ? (
                                            <div className="booking-status-card booking-status-card-error">
                                                {inlineSubmitError}
                                            </div>
                                        ) : null}
                                    </div>

                                    <div className="booking-details-before-card">
                                        {renderBeforeConfirmCard()}
                                    </div>

                                    <div className="booking-summary-mobile">
                                        {renderSummaryCards()}
                                    </div>

                                    <div className="booking-form-actions">
                                        <GoldButton
                                            type="button"
                                            onClick={prevStep}
                                            disabled={releasingHold || submitting}
                                        >
                                            Back
                                        </GoldButton>

                                        <GoldButton
                                            type="submit"
                                            disabled={
                                                submitting ||
                                                releasingHold ||
                                                bookingResult != null ||
                                                !selectedSlot ||
                                                !activeHold?.id ||
                                                !activeHold?.holdAccessToken
                                            }
                                        >
                                            Confirm Booking
                                        </GoldButton>
                                    </div>
                                </form>

                                <div className="booking-details-aside">
                                    {renderAppointmentSummaryCard()}
                                </div>
                            </div>
                        </motion.section>
                    ) : null}
                </div>
            </div>

            <StripeHoldModal
                open={stripeModalOpen}
                prewarm={step === 4 && Boolean(activeHold?.id) && selectedTreatmentPrice != null}
                paymentAmountLabel={formatCurrency(selectedTreatmentPrice)}
                paymentAmountValue={selectedTreatmentPrice}
                currency={checkoutConfig.currency}
                customer={customerForm}
                publishableKey={checkoutConfig.stripePublishableKey}
                loading={submitting}
                serverError=""
                onClose={() => {
                    if (!submitting) {
                        setStripeModalOpen(false);
                    }
                }}
                onAuthorize={authorizeStripeHold}
                onConfirm={handleStripeConfirmation}
            />

            {bookingResult ? (
                <div className="booking-success-backdrop" role="presentation">
                    <motion.div
                        ref={successDialogRef}
                        initial={{ opacity: 0, y: 16, scale: 0.98 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        transition={{ duration: 0.24 }}
                        className="booking-success-card"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="booking-success-title"
                        aria-describedby="booking-success-description"
                        tabIndex={-1}
                    >
                        <button
                            type="button"
                            className="booking-success-close"
                            onClick={handleSuccessClose}
                            aria-label="Close and return home"
                        >
                            <X size={18} />
                        </button>

                        <div className="booking-success-icon">
                            <CheckCircle2 size={26} />
                        </div>

                        <div className="booking-summary-badge booking-summary-badge-success">
                            {bookingResult.status === 'CONFIRMED'
                                ? 'Booking Confirmed'
                                : 'Payment Received'}
                        </div>

                        <h2 id="booking-success-title">
                            {bookingResult.status === 'CONFIRMED'
                                ? 'Your booking is confirmed'
                                : 'We are finalizing your booking'}
                        </h2>
                        <p id="booking-success-description" className="booking-success-description">
                            {bookingResult.status === 'CONFIRMED'
                                ? 'Your payment was completed successfully and your appointment is now locked into the calendar. Review the details below and close this window when you are ready.'
                                : 'Your payment was completed successfully. We are still reconciling the final booking status, and it should refresh automatically within a few seconds.'}
                        </p>

                        <div className="booking-confirmation-grid">
                            <div>
                                <span>Service</span>
                                <strong>{selectedTreatment?.name || '--'}</strong>
                            </div>
                            <div>
                                <span>Barber</span>
                                <strong>{selectedEmployee?.name || '--'}</strong>
                            </div>
                            <div>
                                <span>Date</span>
                                <strong>{formatBookingDate(bookingResult.bookingDate)}</strong>
                            </div>
                            <div>
                                <span>Time</span>
                                <strong>
                                    {bookingResult.startTime && bookingResult.endTime
                                        ? `${formatTime(bookingResult.startTime)} - ${formatTime(bookingResult.endTime)}`
                                        : '--'}
                                </strong>
                            </div>
                            <div>
                                <span>Guest</span>
                                <strong>
                                    {bookingResult.customerName ||
                                        customerForm.customerName ||
                                        '--'}
                                </strong>
                            </div>
                            <div>
                                <span>Email</span>
                                <strong>
                                    {bookingResult.customerEmail ||
                                        customerForm.customerEmail ||
                                        '--'}
                                </strong>
                            </div>
                            <div>
                                <span>Status</span>
                                <strong>{bookingResult.status || 'PENDING'}</strong>
                            </div>
                        </div>
                    </motion.div>
                </div>
            ) : null}
        </div>
    );
}
