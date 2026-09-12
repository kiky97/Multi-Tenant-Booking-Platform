export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const ALL_EMPLOYEES_OPTION = '__ALL_EMPLOYEES__';
export const ADMIN_HOLD_SESSION_STORAGE_KEY = 'admin_booking_hold_session_id';
export const ADMIN_HOLD_STATE_STORAGE_KEY = 'admin_booking_hold_state';
export const ADMIN_HOLD_REFRESH_INTERVAL_MS = 30_000;
export const ADMIN_BOOKING_AVAILABILITY_REFRESH_INTERVAL_MS = 5_000;
export const DEFAULT_SCHEDULE_TIMES = {
    openTime: '09:00',
    closeTime: '17:00'
};

export const todayIso = () => new Date().toISOString().slice(0, 10);

export function isoToDate(value) {
    if (!value) {
        return new Date();
    }

    const [year, month, day] = String(value)
        .split('-')
        .map((part) => Number.parseInt(part, 10));

    if (!year || !month || !day) {
        return new Date();
    }

    return new Date(year, month - 1, day);
}

export function dateToIso(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function isValidIsoDate(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return false;
    }

    const [year, month, day] = value.split('-').map((part) => Number.parseInt(part, 10));

    if (!year || !month || !day) {
        return false;
    }

    return dateToIso(new Date(year, month - 1, day)) === value;
}

export function getEmployeePeriodDateError(startDate, endDate) {
    if (!isValidIsoDate(startDate)) {
        return 'Please choose a real calendar start date before saving the period schedule.';
    }

    if (!isValidIsoDate(endDate)) {
        return 'Please choose a real calendar end date before saving the period schedule.';
    }

    if (startDate > endDate) {
        return 'The end date must be the same as or later than the start date.';
    }

    return '';
}

export function emptyDayConfig() {
    return DAYS.map((day) => ({
        dayOfWeek: day,
        workingDay: false,
        openTime: '',
        closeTime: ''
    }));
}

export function mapHoursResponse(hours) {
    const byDay = new Map(hours.map((item) => [item.dayOfWeek, item]));
    return DAYS.map((day) => {
        const value = byDay.get(day);
        return {
            dayOfWeek: day,
            workingDay: Boolean(value?.workingDay),
            openTime: value?.openTime ? String(value.openTime).slice(0, 5) : '',
            closeTime: value?.closeTime ? String(value.closeTime).slice(0, 5) : ''
        };
    });
}

export function mapScheduleDay(day, dateFallback) {
    const workingDay = Boolean(day?.workingDay);

    return {
        workingDate: day?.workingDate || dateFallback,
        workingDay,
        openTime: day?.openTime
            ? String(day.openTime).slice(0, 5)
            : DEFAULT_SCHEDULE_TIMES.openTime,
        closeTime: day?.closeTime
            ? String(day.closeTime).slice(0, 5)
            : DEFAULT_SCHEDULE_TIMES.closeTime,
        breakStartTime: day?.breakStartTime ? String(day.breakStartTime).slice(0, 5) : '',
        breakEndTime: day?.breakEndTime ? String(day.breakEndTime).slice(0, 5) : ''
    };
}

export function emptyEmployeePeriodConfig() {
    return DAYS.map((day) => ({
        dayOfWeek: day,
        workingDay: false,
        openTime: DEFAULT_SCHEDULE_TIMES.openTime,
        closeTime: DEFAULT_SCHEDULE_TIMES.closeTime,
        breakStartTime: '',
        breakEndTime: ''
    }));
}

export function mapPeriodSettings(periodSettings, fallbackDate) {
    if (!periodSettings) {
        return {
            targetEmployeeId: ALL_EMPLOYEES_OPTION,
            startDate: fallbackDate,
            endDate: fallbackDate,
            days: emptyEmployeePeriodConfig()
        };
    }

    const byDay = new Map((periodSettings.days || []).map((item) => [item.dayOfWeek, item]));

    return {
        targetEmployeeId:
            periodSettings.applyToAllEmployees || !periodSettings.employeeId
                ? ALL_EMPLOYEES_OPTION
                : periodSettings.employeeId,
        startDate: periodSettings.startDate || fallbackDate,
        endDate: periodSettings.endDate || fallbackDate,
        days: DAYS.map((day) => {
            const value = byDay.get(day);
            return {
                dayOfWeek: day,
                workingDay: Boolean(value?.workingDay),
                openTime: value?.openTime
                    ? String(value.openTime).slice(0, 5)
                    : DEFAULT_SCHEDULE_TIMES.openTime,
                closeTime: value?.closeTime
                    ? String(value.closeTime).slice(0, 5)
                    : DEFAULT_SCHEDULE_TIMES.closeTime,
                breakStartTime: value?.breakStartTime
                    ? String(value.breakStartTime).slice(0, 5)
                    : '',
                breakEndTime: value?.breakEndTime ? String(value.breakEndTime).slice(0, 5) : ''
            };
        })
    };
}

export function formatDayLabel(day) {
    return day.charAt(0) + day.slice(1).toLowerCase();
}

export function toBookingOverview(bookingOverview) {
    return {
        bookings: bookingOverview?.bookings || [],
        confirmedCount: bookingOverview?.confirmedCount || 0,
        filteredCount: bookingOverview?.filteredCount || 0
    };
}

export function toConfirmedReviewOverview(bookingOverview) {
    const normalizedOverview = toBookingOverview(bookingOverview);
    const confirmedBookings = normalizedOverview.bookings.filter(
        (booking) => booking.status === 'CONFIRMED'
    );

    return {
        bookings: confirmedBookings,
        confirmedCount: normalizedOverview.confirmedCount,
        filteredCount: confirmedBookings.length
    };
}

export function normalizeTimeValue(value) {
    return value ? String(value).slice(0, 5) : '';
}

export function areSameSlot(left, right) {
    return Boolean(
        left &&
        right &&
        normalizeTimeValue(left.startTime) === normalizeTimeValue(right.startTime) &&
        normalizeTimeValue(left.endTime) === normalizeTimeValue(right.endTime)
    );
}

export function toHeldSlot(slot) {
    if (!slot) {
        return null;
    }

    return {
        ...slot,
        startTime: normalizeTimeValue(slot.startTime),
        endTime: normalizeTimeValue(slot.endTime),
        available: false,
        status: 'HELD'
    };
}

export function releaseSlotFromList(slots, releasedSlot) {
    if (!Array.isArray(slots) || !releasedSlot) {
        return Array.isArray(slots) ? slots : [];
    }

    return slots.map((slot) =>
        areSameSlot(slot, releasedSlot)
            ? {
                  ...slot,
                  available: true,
                  status: 'AVAILABLE'
              }
            : slot
    );
}

export function markHeldSlotInList(slots, heldSlot) {
    if (!Array.isArray(slots) || !heldSlot) {
        return Array.isArray(slots) ? slots : [];
    }

    return slots.map((slot) =>
        areSameSlot(slot, heldSlot)
            ? {
                  ...slot,
                  available: false,
                  status: 'HELD'
              }
            : slot
    );
}

export function createClientSessionId() {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }

    return `admin-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function getOrCreateAdminHoldSessionId() {
    if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
        return createClientSessionId();
    }

    const stored = window.sessionStorage.getItem(ADMIN_HOLD_SESSION_STORAGE_KEY);
    if (stored) {
        return stored;
    }

    const next = createClientSessionId();
    window.sessionStorage.setItem(ADMIN_HOLD_SESSION_STORAGE_KEY, next);
    return next;
}

export function readStoredAdminHoldState() {
    if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
        return null;
    }

    const raw = window.sessionStorage.getItem(ADMIN_HOLD_STATE_STORAGE_KEY);
    if (!raw) {
        return null;
    }

    try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed.id === 'string' ? parsed : null;
    } catch {
        return null;
    }
}

export function persistAdminHoldState(hold) {
    if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
        return;
    }

    if (!hold?.id) {
        window.sessionStorage.removeItem(ADMIN_HOLD_STATE_STORAGE_KEY);
        return;
    }

    window.sessionStorage.setItem(ADMIN_HOLD_STATE_STORAGE_KEY, JSON.stringify(hold));
}

export function emptyBookingEditForm() {
    return {
        id: '',
        bookingDate: '',
        startTime: '',
        endTime: '',
        employeeId: '',
        treatmentId: '',
        customerName: '',
        customerPhone: '',
        customerEmail: '',
        holdAmount: '',
        status: 'CONFIRMED'
    };
}

export function toBookingEditForm(booking) {
    if (!booking) {
        return emptyBookingEditForm();
    }

    return {
        id: booking.id || '',
        bookingDate: booking.bookingDate || '',
        startTime: booking.startTime ? String(booking.startTime).slice(0, 5) : '',
        endTime: booking.endTime ? String(booking.endTime).slice(0, 5) : '',
        employeeId: booking.employeeId || '',
        treatmentId: booking.treatmentId || '',
        customerName: booking.customerName || '',
        customerPhone: booking.customerPhone || '',
        customerEmail: booking.customerEmail || '',
        holdAmount: booking.holdAmount != null ? String(booking.holdAmount) : '',
        status: booking.status || 'CONFIRMED'
    };
}

export function isDateWithinRange(date, startDate, endDate) {
    return Boolean(date && startDate && endDate && date >= startDate && date <= endDate);
}

export function normalizeIdList(values) {
    if (!Array.isArray(values)) {
        return [];
    }

    return [...new Set(values.filter((value) => typeof value === 'string' && value.trim()))];
}

export function getEmployeeTreatmentIds(employee) {
    return normalizeIdList(employee?.treatmentIds);
}

export function doesEmployeeProvideTreatment(employee, treatmentId) {
    if (!employee || !treatmentId) {
        return false;
    }

    return getEmployeeTreatmentIds(employee).includes(treatmentId);
}

export function normalizePhoneDigits(value) {
    if (typeof value !== 'string') {
        return '';
    }

    return value.replace(/\D/g, '');
}

export function areSalonHoursEqual(left, right) {
    return (
        Boolean(left && right) &&
        left.dayOfWeek === right.dayOfWeek &&
        Boolean(left.workingDay) === Boolean(right.workingDay) &&
        (left.openTime || '') === (right.openTime || '') &&
        (left.closeTime || '') === (right.closeTime || '')
    );
}

export function getDayOfWeekFromIso(value) {
    const date = isoToDate(value);
    const jsDay = date.getDay();
    return DAYS[(jsDay + 6) % 7];
}

export function startOfIsoWeek(value) {
    const date = isoToDate(value);
    const diff = (date.getDay() + 6) % 7;
    date.setDate(date.getDate() - diff);
    return dateToIso(date);
}

export function mapScheduleResponseToPeriodDays(scheduleDays) {
    const byDay = new Map(
        (scheduleDays || []).map((item) => [getDayOfWeekFromIso(item.workingDate), item])
    );

    return DAYS.map((day) => {
        const value = byDay.get(day);

        return {
            dayOfWeek: day,
            workingDay: Boolean(value?.workingDay),
            openTime: value?.openTime
                ? String(value.openTime).slice(0, 5)
                : DEFAULT_SCHEDULE_TIMES.openTime,
            closeTime: value?.closeTime
                ? String(value.closeTime).slice(0, 5)
                : DEFAULT_SCHEDULE_TIMES.closeTime,
            breakStartTime: value?.breakStartTime ? String(value.breakStartTime).slice(0, 5) : '',
            breakEndTime: value?.breakEndTime ? String(value.breakEndTime).slice(0, 5) : ''
        };
    });
}
