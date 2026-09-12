import { useEffect, useMemo, useState } from 'react';
import {
    CalendarClock,
    Clock3,
    Mail,
    Phone,
    Scissors,
    Search,
    ShieldAlert,
    Sparkles,
    UserRound
} from 'lucide-react';
import BookingCalendar from '../../components/booking/BookingCalendar';
import GoldButton from '../../components/ui/GoldButton';
import LuxuryCard from '../../components/ui/LuxuryCard';
import SectionTitle from '../../components/ui/SectionTitle';
import TimeSlotList from '../../components/booking/TimeSlotList';
import {
    getBookingEditEmployeeOptions,
    getBookingEditFormForEmployeeChange,
    getBookingEditFormForTreatmentChange,
    getBookingEditTreatmentOptions,
    normalizeBookingEditSelection
} from '../utils/bookingEditSelection';

function formatCurrency(value) {
    if (value == null || value === '') {
        return '--';
    }

    return new Intl.NumberFormat('en-IE', {
        style: 'currency',
        currency: 'EUR'
    }).format(Number(value));
}

function formatBookingDate(value) {
    if (!value) {
        return '--';
    }

    const [year, month, day] = String(value)
        .split('-')
        .map((part) => Number.parseInt(part, 10));
    if (!year || !month || !day) {
        return value;
    }

    return new Intl.DateTimeFormat('en-IE', {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        year: 'numeric'
    }).format(new Date(year, month - 1, day));
}

function formatSlotLabel(slot) {
    if (!slot) {
        return '--';
    }

    return `${slot.startTime.slice(0, 5)} - ${slot.endTime.slice(0, 5)}`;
}

function formatTimestamp(value) {
    if (!value) {
        return '--';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat('en-IE', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
}

function toStatusModifier(status) {
    return String(status || 'UNKNOWN').toLowerCase();
}

const BOOKING_EDIT_STATUSES = ['CONFIRMED', 'CANCELLED', 'PENDING', 'DONE', 'EXPIRED'];

function scrollToAdminSection(sectionId) {
    if (typeof document === 'undefined') {
        return;
    }

    document.getElementById(sectionId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
    });
}

export default function BookingsPage({
    sortedEmployees,
    filteredManualBookingEmployees,
    sortedTreatments,
    manualBookingEmployeeId,
    setManualBookingEmployeeId,
    manualBookingTreatmentId,
    setManualBookingTreatmentId,
    manualBookingDate,
    manualBookingDateObject,
    onManualBookingDateSelect,
    manualBookingSlots,
    manualBookingSelectedSlot,
    onManualBookingSlotSelect,
    manualBookingSlotsLoading,
    manualBookingHoldLoading,
    manualBookingForm,
    setManualBookingForm,
    manualBookingLookupLoading,
    manualBookingLookupMessage,
    clearFieldError,
    fieldErrors,
    sectionErrors,
    sectionSuccess,
    loading,
    createAdminBooking,
    confirmedReviewOverview,
    confirmedReviewSearchQuery,
    setConfirmedReviewSearchQuery,
    refreshConfirmedReview,
    cancelBookingReview,
    bookingEditForm,
    setBookingEditForm,
    fillBookingEditForm,
    clearBookingEditForm,
    updateBookingReview,
    allBookingsOverview,
    bookingSearchQuery,
    setBookingSearchQuery,
    refreshAllBookings,
    bookingBlacklistEntries,
    bookingBlacklistForm,
    setBookingBlacklistForm,
    createBookingBlacklistEntry,
    deleteBookingBlacklistEntry,
    refreshBookingBlacklist
}) {
    const [visibleBookingCount, setVisibleBookingCount] = useState(10);
    const selectedEmployee =
        sortedEmployees.find((item) => item.id === manualBookingEmployeeId) || null;
    const selectedTreatment =
        sortedTreatments.find((item) => item.id === manualBookingTreatmentId) || null;
    const confirmedReviewBookings = confirmedReviewOverview?.bookings || [];
    const allBookings = useMemo(
        () => allBookingsOverview?.bookings || [],
        [allBookingsOverview?.bookings]
    );
    const visibleBookings = useMemo(
        () => allBookings.slice(0, visibleBookingCount),
        [allBookings, visibleBookingCount]
    );
    const canShowMoreBookings = visibleBookings.length < allBookings.length;
    const canCollapseBookings = allBookings.length > 10 && visibleBookingCount > 10;
    const bookingEditEmployeeOptions = useMemo(
        () => getBookingEditEmployeeOptions(sortedEmployees, bookingEditForm.treatmentId),
        [bookingEditForm.treatmentId, sortedEmployees]
    );
    const bookingEditTreatmentOptions = useMemo(
        () =>
            getBookingEditTreatmentOptions(
                sortedEmployees,
                sortedTreatments,
                bookingEditForm.employeeId
            ),
        [bookingEditForm.employeeId, sortedEmployees, sortedTreatments]
    );

    useEffect(() => {
        setVisibleBookingCount(10);
    }, [bookingSearchQuery, allBookingsOverview?.filteredCount]);

    useEffect(() => {
        setBookingEditForm((current) => {
            const nextForm = normalizeBookingEditSelection(
                current,
                sortedEmployees,
                sortedTreatments
            );

            if (
                nextForm.employeeId === current.employeeId &&
                nextForm.treatmentId === current.treatmentId
            ) {
                return current;
            }

            return nextForm;
        });
    }, [
        bookingEditForm.treatmentId,
        bookingEditForm.employeeId,
        setBookingEditForm,
        sortedEmployees,
        sortedTreatments
    ]);

    const bookingSections = [
        { id: 'admin-phone-booking', label: 'Phone Booking' },
        { id: 'admin-confirmed-review', label: 'Confirmed Review' },
        { id: 'admin-booking-data', label: "Booking's Data" },
        { id: 'admin-all-bookings', label: 'All Bookings' },
        { id: 'admin-blacklist', label: 'Blacklist' }
    ];

    return (
        <div className="admin-page-stack">
            <section className="panel">
                <SectionTitle title="Bookings" subtitle="Admin Booking Workspace" />
                <p className="panel-note">
                    Everything related to appointments now lives in one place: free phone bookings,
                    Stripe review, the live booking schedule, and blocked contacts.
                </p>

                <div className="admin-subsection-nav">
                    {bookingSections.map((section) => (
                        <button
                            key={section.id}
                            type="button"
                            className="btn-gold admin-subsection-link"
                            onClick={() => scrollToAdminSection(section.id)}
                        >
                            {section.label}
                        </button>
                    ))}
                </div>
            </section>

            <section id="admin-phone-booking" className="panel admin-workspace-section">
                <div className="admin-booking-public-shell booking-page-shell">
                    <SectionTitle title="Book Appointment" subtitle="Free Admin Booking" />
                    <p className="panel-note">
                        Use this section for phone bookings. Selecting a slot holds it immediately
                        without Stripe, and saving the form converts that hold into the real
                        booking.
                    </p>

                    <div className="admin-booking-grid">
                        <div className="admin-booking-main">
                            <LuxuryCard className="booking-form-panel admin-booking-selection-card">
                                <div className="admin-booking-selection-grid">
                                    <label>
                                        Treatment
                                        <select
                                            className="admin-choice-select"
                                            value={manualBookingTreatmentId}
                                            onChange={(event) =>
                                                setManualBookingTreatmentId(event.target.value)
                                            }
                                        >
                                            <option value="">Select treatment</option>
                                            {sortedTreatments.map((item) => (
                                                <option key={item.id} value={item.id}>
                                                    {item.name}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        Employee
                                        <select
                                            className="admin-choice-select"
                                            value={manualBookingEmployeeId}
                                            disabled={filteredManualBookingEmployees.length === 0}
                                            onChange={(event) =>
                                                setManualBookingEmployeeId(event.target.value)
                                            }
                                        >
                                            <option value="">Select employee</option>
                                            {filteredManualBookingEmployees.map((item) => (
                                                <option key={item.id} value={item.id}>
                                                    {item.name}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                </div>
                                {manualBookingTreatmentId &&
                                filteredManualBookingEmployees.length === 0 ? (
                                    <p className="panel-note">
                                        No bookable employees currently provide the selected
                                        service.
                                    </p>
                                ) : null}
                            </LuxuryCard>

                            <div className="admin-booking-datetime-grid">
                                <div className="booking-date-panel admin-booking-calendar-panel">
                                    <BookingCalendar
                                        selectedDate={manualBookingDateObject}
                                        onSelect={onManualBookingDateSelect}
                                    />
                                </div>

                                <div className="booking-time-panel admin-booking-time-panel">
                                    <TimeSlotList
                                        slots={manualBookingSlots}
                                        selectedSlot={manualBookingSelectedSlot}
                                        onSelect={onManualBookingSlotSelect}
                                        loading={
                                            manualBookingSlotsLoading || manualBookingHoldLoading
                                        }
                                        emptyMessage="No free slots for the selected employee, service, and date."
                                    />
                                </div>
                            </div>

                            <LuxuryCard className="booking-form-panel admin-booking-customer-card">
                                <div className="admin-card-heading">
                                    <div className="booking-summary-badge">Customer Details</div>
                                </div>
                                <div className="admin-card-body">
                                    <div className="booking-form-grid admin-booking-form-grid">
                                        <label className="booking-form-field">
                                            <span>
                                                <UserRound size={15} />
                                                Full name
                                            </span>
                                            <input
                                                className="payment-input"
                                                value={manualBookingForm.customerName}
                                                onChange={(event) => {
                                                    setManualBookingForm((current) => ({
                                                        ...current,
                                                        customerName: event.target.value
                                                    }));
                                                    clearFieldError('adminBooking', 'customerName');
                                                }}
                                                placeholder="Customer full name"
                                            />
                                            {fieldErrors.adminBooking?.customerName ? (
                                                <small>
                                                    {fieldErrors.adminBooking.customerName}
                                                </small>
                                            ) : null}
                                        </label>

                                        <label className="booking-form-field">
                                            <span>
                                                <Phone size={15} />
                                                Phone number
                                            </span>
                                            <input
                                                className="payment-input"
                                                value={manualBookingForm.customerPhone}
                                                onChange={(event) => {
                                                    setManualBookingForm((current) => ({
                                                        ...current,
                                                        customerPhone: event.target.value
                                                    }));
                                                    clearFieldError(
                                                        'adminBooking',
                                                        'customerPhone'
                                                    );
                                                }}
                                                placeholder="+353 87 000 0000"
                                            />
                                            {fieldErrors.adminBooking?.customerPhone ? (
                                                <small>
                                                    {fieldErrors.adminBooking.customerPhone}
                                                </small>
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
                                                value={manualBookingForm.customerEmail}
                                                onChange={(event) => {
                                                    setManualBookingForm((current) => ({
                                                        ...current,
                                                        customerEmail: event.target.value
                                                    }));
                                                    clearFieldError(
                                                        'adminBooking',
                                                        'customerEmail'
                                                    );
                                                }}
                                                placeholder="customer@example.com"
                                            />
                                            {fieldErrors.adminBooking?.customerEmail ? (
                                                <small>
                                                    {fieldErrors.adminBooking.customerEmail}
                                                </small>
                                            ) : null}
                                        </label>
                                    </div>

                                    {manualBookingLookupLoading ? (
                                        <p className="admin-inline-note">
                                            Looking up previous customer details...
                                        </p>
                                    ) : null}
                                    {!manualBookingLookupLoading && manualBookingLookupMessage ? (
                                        <p className="admin-inline-note admin-inline-note-success">
                                            {manualBookingLookupMessage}
                                        </p>
                                    ) : null}

                                    <div className="row admin-card-actions">
                                        <GoldButton
                                            type="button"
                                            onClick={createAdminBooking}
                                            disabled={
                                                loading ||
                                                !manualBookingSelectedSlot ||
                                                !manualBookingEmployeeId ||
                                                !manualBookingTreatmentId ||
                                                !manualBookingForm.customerName.trim() ||
                                                !manualBookingForm.customerPhone.trim()
                                            }
                                        >
                                            Save Phone Booking
                                        </GoldButton>
                                    </div>

                                    {sectionErrors.adminBooking ? (
                                        <p className="section-error">
                                            {sectionErrors.adminBooking}
                                        </p>
                                    ) : null}
                                    {sectionSuccess.adminBooking ? (
                                        <p className="section-ok">{sectionSuccess.adminBooking}</p>
                                    ) : null}
                                </div>
                            </LuxuryCard>
                        </div>

                        <div className="admin-booking-aside">
                            <LuxuryCard className="booking-summary-card admin-booking-summary-card">
                                <div className="admin-summary-header">
                                    <div className="booking-summary-badge">Appointment Summary</div>
                                    <h2>Phone Booking Preview</h2>
                                    <p className="booking-summary-description">
                                        Confirm the slot details before you update the calendar and
                                        block the appointment on the public site.
                                    </p>
                                </div>
                                <div className="ornament admin-summary-ornament !mt-0 !w-full" />
                                <div className="booking-summary-list admin-booking-summary-list">
                                    <div className="admin-summary-list-item">
                                        <span className="admin-summary-list-key">
                                            <Sparkles size={14} />
                                            <span className="admin-summary-list-label">
                                                Service
                                            </span>
                                        </span>
                                        <strong className="admin-summary-list-value">
                                            {selectedTreatment?.name || '--'}
                                        </strong>
                                    </div>
                                    <div className="admin-summary-list-item">
                                        <span className="admin-summary-list-key">
                                            <Scissors size={14} />
                                            <span className="admin-summary-list-label">
                                                Employee
                                            </span>
                                        </span>
                                        <strong className="admin-summary-list-value">
                                            {selectedEmployee?.name || '--'}
                                        </strong>
                                    </div>
                                    <div className="admin-summary-list-item">
                                        <span className="admin-summary-list-key">
                                            <CalendarClock size={14} />
                                            <span className="admin-summary-list-label">Date</span>
                                        </span>
                                        <strong className="admin-summary-list-value">
                                            {formatBookingDate(manualBookingDate)}
                                        </strong>
                                    </div>
                                    <div className="admin-summary-list-item">
                                        <span className="admin-summary-list-key">
                                            <Clock3 size={14} />
                                            <span className="admin-summary-list-label">Time</span>
                                        </span>
                                        <strong className="admin-summary-list-value">
                                            {formatSlotLabel(manualBookingSelectedSlot)}
                                        </strong>
                                    </div>
                                    <div className="admin-summary-list-item">
                                        <span className="admin-summary-list-key">
                                            <Phone size={14} />
                                            <span className="admin-summary-list-label">Phone</span>
                                        </span>
                                        <strong className="admin-summary-list-value">
                                            {manualBookingForm.customerPhone || '--'}
                                        </strong>
                                    </div>
                                    <div className="admin-summary-list-item">
                                        <span className="admin-summary-list-key">
                                            <Mail size={14} />
                                            <span className="admin-summary-list-label">Email</span>
                                        </span>
                                        <strong className="admin-summary-list-value">
                                            {manualBookingForm.customerEmail || '--'}
                                        </strong>
                                    </div>
                                </div>
                            </LuxuryCard>
                        </div>
                    </div>
                </div>
            </section>

            <section id="admin-confirmed-review" className="panel admin-workspace-section">
                <SectionTitle
                    title="Confirmed Booking Review"
                    subtitle="Confirmed Appointment Search"
                />
                <p className="panel-note">
                    Search only through confirmed bookings to quickly verify upcoming appointments
                    that are already locked into the calendar.
                </p>

                <div className="row admin-booking-search-row">
                    <label className="admin-search-field">
                        <span>
                            <Search size={14} />
                            Search confirmed booking
                        </span>
                        <input
                            className="payment-input"
                            value={confirmedReviewSearchQuery}
                            onChange={(event) => setConfirmedReviewSearchQuery(event.target.value)}
                            placeholder="Type customer name, phone or email"
                        />
                    </label>

                    <button
                        type="button"
                        className="btn-gold"
                        onClick={refreshConfirmedReview}
                        disabled={loading}
                    >
                        Refresh List
                    </button>
                </div>

                <div className="admin-summary-strip admin-summary-strip-single">
                    <div className="admin-summary-pill">
                        <span>Visible confirmed bookings</span>
                        <strong>{confirmedReviewOverview.filteredCount || 0}</strong>
                    </div>
                </div>

                <div className="table-wrapper admin-table-shell admin-table-shell-wide">
                    <table className="admin-data-table admin-data-table-bookings">
                        <thead>
                            <tr>
                                <th>Booking Date</th>
                                <th>Time</th>
                                <th>Employee</th>
                                <th>Service</th>
                                <th>Customer</th>
                                <th>Phone</th>
                                <th>Email</th>
                                <th>Amount</th>
                                <th>Created</th>
                                <th>Booking ID</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {confirmedReviewBookings.length === 0 ? (
                                <tr>
                                    <td colSpan={11}>
                                        {confirmedReviewSearchQuery.trim()
                                            ? 'No confirmed bookings match the current search.'
                                            : 'No confirmed bookings are available yet.'}
                                    </td>
                                </tr>
                            ) : (
                                confirmedReviewBookings.map((booking) => (
                                    <tr key={booking.id}>
                                        <td>{formatBookingDate(booking.bookingDate)}</td>
                                        <td>
                                            {booking.startTime} - {booking.endTime}
                                        </td>
                                        <td>{booking.employeeName || '-'}</td>
                                        <td>{booking.treatmentName || '-'}</td>
                                        <td>{booking.customerName || '-'}</td>
                                        <td>{booking.customerPhone || '-'}</td>
                                        <td>{booking.customerEmail || '-'}</td>
                                        <td>{formatCurrency(booking.holdAmount)}</td>
                                        <td>{formatTimestamp(booking.createdAt)}</td>
                                        <td className="code">{booking.id}</td>
                                        <td className="row admin-table-actions">
                                            <button
                                                type="button"
                                                className="btn-gold admin-inline-btn"
                                                onClick={() => {
                                                    fillBookingEditForm(booking);
                                                    scrollToAdminSection('admin-booking-data');
                                                }}
                                                disabled={loading}
                                            >
                                                Edit
                                            </button>
                                            <button
                                                type="button"
                                                className="btn-gold admin-inline-btn"
                                                onClick={() => cancelBookingReview(booking)}
                                                disabled={loading}
                                            >
                                                Cancel
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>

                {sectionErrors.confirmedBookingReview ? (
                    <p className="section-error">{sectionErrors.confirmedBookingReview}</p>
                ) : null}
                {sectionSuccess.confirmedBookingReview ? (
                    <p className="section-ok">{sectionSuccess.confirmedBookingReview}</p>
                ) : null}
            </section>

            <section id="admin-booking-data" className="panel admin-workspace-section">
                <SectionTitle title="Booking's Data" subtitle="Edit Existing Appointment" />
                <p className="panel-note">
                    Open a confirmed booking with Edit, update the appointment details below, and
                    save the changes back to the backend. Clear only resets this form locally.
                </p>

                <form className="grid two" onSubmit={(event) => event.preventDefault()}>
                    <label>
                        Booking Date
                        <input
                            type="date"
                            value={bookingEditForm.bookingDate}
                            onChange={(event) => {
                                setBookingEditForm((current) => ({
                                    ...current,
                                    bookingDate: event.target.value
                                }));
                                clearFieldError('bookingEdit', 'bookingDate');
                            }}
                        />
                        {fieldErrors.bookingEdit?.bookingDate ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.bookingDate}
                            </span>
                        ) : null}
                    </label>

                    <label className="admin-booking-time-field">
                        Time
                        <div className="admin-booking-time-range">
                            <input
                                type="time"
                                value={bookingEditForm.startTime}
                                onChange={(event) => {
                                    setBookingEditForm((current) => ({
                                        ...current,
                                        startTime: event.target.value
                                    }));
                                    clearFieldError('bookingEdit', 'startTime');
                                }}
                            />
                            <input
                                type="time"
                                value={bookingEditForm.endTime}
                                onChange={(event) => {
                                    setBookingEditForm((current) => ({
                                        ...current,
                                        endTime: event.target.value
                                    }));
                                    clearFieldError('bookingEdit', 'endTime');
                                }}
                            />
                        </div>
                        {fieldErrors.bookingEdit?.startTime ? (
                            <span className="field-error">{fieldErrors.bookingEdit.startTime}</span>
                        ) : null}
                        {fieldErrors.bookingEdit?.endTime ? (
                            <span className="field-error">{fieldErrors.bookingEdit.endTime}</span>
                        ) : null}
                    </label>

                    <label>
                        Employee
                        <select
                            className="admin-choice-select"
                            disabled={bookingEditEmployeeOptions.length === 0}
                            value={bookingEditForm.employeeId}
                            onChange={(event) => {
                                const nextEmployeeId = event.target.value;
                                setBookingEditForm((current) =>
                                    getBookingEditFormForEmployeeChange(
                                        current,
                                        nextEmployeeId,
                                        sortedEmployees,
                                        sortedTreatments
                                    )
                                );
                                clearFieldError('bookingEdit', 'employeeId');
                            }}
                        >
                            <option value="">Select employee</option>
                            {bookingEditEmployeeOptions.map((item) => (
                                <option key={item.id} value={item.id}>
                                    {item.name}
                                </option>
                            ))}
                        </select>
                        {fieldErrors.bookingEdit?.employeeId ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.employeeId}
                            </span>
                        ) : null}
                        {bookingEditForm.treatmentId && bookingEditEmployeeOptions.length === 0 ? (
                            <span className="panel-note">
                                No bookable employees currently provide the selected service.
                            </span>
                        ) : null}
                    </label>

                    <label>
                        Service
                        <select
                            className="admin-choice-select"
                            disabled={bookingEditTreatmentOptions.length === 0}
                            value={bookingEditForm.treatmentId}
                            onChange={(event) => {
                                const nextTreatmentId = event.target.value;
                                setBookingEditForm((current) =>
                                    getBookingEditFormForTreatmentChange(
                                        current,
                                        nextTreatmentId,
                                        sortedEmployees
                                    )
                                );
                                clearFieldError('bookingEdit', 'treatmentId');
                            }}
                        >
                            <option value="">Select service</option>
                            {bookingEditTreatmentOptions.map((item) => (
                                <option key={item.id} value={item.id}>
                                    {item.name}
                                </option>
                            ))}
                        </select>
                        {fieldErrors.bookingEdit?.treatmentId ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.treatmentId}
                            </span>
                        ) : null}
                        {bookingEditForm.employeeId && bookingEditTreatmentOptions.length === 0 ? (
                            <span className="panel-note">
                                This employee does not currently provide any bookable services.
                            </span>
                        ) : null}
                    </label>

                    <label>
                        Customer
                        <input
                            className="payment-input"
                            value={bookingEditForm.customerName}
                            onChange={(event) => {
                                setBookingEditForm((current) => ({
                                    ...current,
                                    customerName: event.target.value
                                }));
                                clearFieldError('bookingEdit', 'customerName');
                            }}
                            placeholder="Customer full name"
                        />
                        {fieldErrors.bookingEdit?.customerName ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.customerName}
                            </span>
                        ) : null}
                    </label>

                    <label>
                        Phone
                        <input
                            className="payment-input"
                            value={bookingEditForm.customerPhone}
                            onChange={(event) => {
                                setBookingEditForm((current) => ({
                                    ...current,
                                    customerPhone: event.target.value
                                }));
                                clearFieldError('bookingEdit', 'customerPhone');
                            }}
                            placeholder="+353 87 000 0000"
                        />
                        {fieldErrors.bookingEdit?.customerPhone ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.customerPhone}
                            </span>
                        ) : null}
                    </label>

                    <label>
                        Email
                        <input
                            className="payment-input"
                            type="email"
                            value={bookingEditForm.customerEmail}
                            onChange={(event) => {
                                setBookingEditForm((current) => ({
                                    ...current,
                                    customerEmail: event.target.value
                                }));
                                clearFieldError('bookingEdit', 'customerEmail');
                            }}
                            placeholder="customer@example.com"
                        />
                        {fieldErrors.bookingEdit?.customerEmail ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.customerEmail}
                            </span>
                        ) : null}
                    </label>

                    <label>
                        Amount
                        <input
                            className="payment-input"
                            type="number"
                            min="0"
                            step="0.01"
                            value={bookingEditForm.holdAmount}
                            onChange={(event) => {
                                setBookingEditForm((current) => ({
                                    ...current,
                                    holdAmount: event.target.value
                                }));
                                clearFieldError('bookingEdit', 'holdAmount');
                            }}
                            placeholder="0.00"
                        />
                        {fieldErrors.bookingEdit?.holdAmount ? (
                            <span className="field-error">
                                {fieldErrors.bookingEdit.holdAmount}
                            </span>
                        ) : null}
                    </label>

                    <label>
                        Status
                        <select
                            className="admin-choice-select"
                            value={bookingEditForm.status}
                            onChange={(event) => {
                                setBookingEditForm((current) => ({
                                    ...current,
                                    status: event.target.value
                                }));
                                clearFieldError('bookingEdit', 'status');
                            }}
                        >
                            {BOOKING_EDIT_STATUSES.map((status) => (
                                <option key={status} value={status}>
                                    {status}
                                </option>
                            ))}
                        </select>
                        {fieldErrors.bookingEdit?.status ? (
                            <span className="field-error">{fieldErrors.bookingEdit.status}</span>
                        ) : null}
                    </label>
                </form>

                <div className="row admin-form-actions">
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={updateBookingReview}
                        disabled={loading || !bookingEditForm.id}
                    >
                        Update
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={clearBookingEditForm}
                        disabled={loading}
                    >
                        Clear
                    </button>
                </div>

                {sectionErrors.bookingEdit ? (
                    <p className="section-error">{sectionErrors.bookingEdit}</p>
                ) : null}
                {sectionSuccess.bookingEdit ? (
                    <p className="section-ok">{sectionSuccess.bookingEdit}</p>
                ) : null}
            </section>

            <section id="admin-all-bookings" className="panel admin-workspace-section">
                <SectionTitle title="All Bookings" subtitle="Search & Schedule Overview" />
                <p className="panel-note">
                    Search by customer name, phone, or email. Upcoming appointments are shown first,
                    while older history follows below.
                </p>

                <div className="row admin-booking-search-row">
                    <label className="admin-search-field">
                        <span>
                            <Search size={14} />
                            Search booking
                        </span>
                        <input
                            className="payment-input"
                            value={bookingSearchQuery}
                            onChange={(event) => setBookingSearchQuery(event.target.value)}
                            placeholder="Type customer name, phone or email"
                        />
                    </label>

                    <button
                        type="button"
                        className="btn-gold"
                        onClick={refreshAllBookings}
                        disabled={loading}
                    >
                        Refresh List
                    </button>
                </div>

                <div className="admin-summary-strip">
                    <div className="admin-summary-pill">
                        <span>Visible bookings</span>
                        <strong>{allBookingsOverview.filteredCount || 0}</strong>
                    </div>
                    <div className="admin-summary-pill">
                        <span>Confirmed total</span>
                        <strong>{allBookingsOverview.confirmedCount || 0}</strong>
                    </div>
                </div>

                <div className="table-wrapper admin-table-shell admin-table-shell-wide">
                    <table className="admin-data-table admin-data-table-bookings">
                        <thead>
                            <tr>
                                <th>Status</th>
                                <th>Date</th>
                                <th>Time</th>
                                <th>Employee</th>
                                <th>Service</th>
                                <th>Customer</th>
                                <th>Phone</th>
                                <th>Email</th>
                                <th>Amount</th>
                                <th>Stripe</th>
                                <th>Created</th>
                                <th>Booking ID</th>
                            </tr>
                        </thead>
                        <tbody>
                            {visibleBookings.length ? (
                                visibleBookings.map((booking) => (
                                    <tr key={booking.id}>
                                        <td>
                                            <span
                                                className={`admin-status-pill admin-status-pill-${toStatusModifier(booking.status)}`}
                                            >
                                                {booking.status || 'UNKNOWN'}
                                            </span>
                                        </td>
                                        <td>{formatBookingDate(booking.bookingDate)}</td>
                                        <td>
                                            {booking.startTime} - {booking.endTime}
                                        </td>
                                        <td>{booking.employeeName || '-'}</td>
                                        <td>{booking.treatmentName || '-'}</td>
                                        <td>{booking.customerName || '-'}</td>
                                        <td>{booking.customerPhone || '-'}</td>
                                        <td>{booking.customerEmail || '-'}</td>
                                        <td>{formatCurrency(booking.holdAmount)}</td>
                                        <td>{booking.stripePaymentStatus || '-'}</td>
                                        <td>{formatTimestamp(booking.createdAt)}</td>
                                        <td className="code">{booking.id}</td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan={12}>
                                        {bookingSearchQuery.trim()
                                            ? 'No bookings match the current search.'
                                            : 'No bookings are available yet.'}
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>

                {allBookings.length > 10 ? (
                    <div className="row admin-booking-list-actions">
                        {canShowMoreBookings ? (
                            <button
                                type="button"
                                className="btn-gold"
                                onClick={() => setVisibleBookingCount((current) => current + 10)}
                                disabled={loading}
                            >
                                Show 10 More
                            </button>
                        ) : null}

                        {canCollapseBookings ? (
                            <button
                                type="button"
                                className="btn-gold"
                                onClick={() => setVisibleBookingCount(10)}
                                disabled={loading}
                            >
                                Collapse List
                            </button>
                        ) : null}
                    </div>
                ) : null}

                <p className="panel-note admin-booking-count">
                    Showing <strong>{visibleBookings.length || 0}</strong> of{' '}
                    <strong>{allBookingsOverview.filteredCount || 0}</strong> bookings. Confirmed
                    bookings total: <strong>{allBookingsOverview.confirmedCount || 0}</strong>
                </p>

                {sectionErrors.bookingList ? (
                    <p className="section-error">{sectionErrors.bookingList}</p>
                ) : null}
                {sectionSuccess.bookingList ? (
                    <p className="section-ok">{sectionSuccess.bookingList}</p>
                ) : null}
            </section>

            <section id="admin-blacklist" className="panel admin-workspace-section">
                <SectionTitle title="Blacklist" subtitle="Blocked Booking Contacts" />
                <p className="panel-note">
                    Contacts from this list cannot complete a booking on the public website, and
                    staff cannot save a phone booking for them from the admin panel.
                </p>

                <div className="admin-booking-blacklist-grid">
                    <LuxuryCard className="admin-blacklist-form-card">
                        <div className="admin-card-heading">
                            <div className="booking-summary-badge">Add To Blacklist</div>
                        </div>
                        <div className="admin-card-body">
                            <div className="grid admin-blacklist-form-grid">
                                <label className="booking-form-field">
                                    <span>
                                        <Mail size={15} />
                                        Email
                                    </span>
                                    <input
                                        className="payment-input"
                                        type="email"
                                        value={bookingBlacklistForm.email}
                                        onChange={(event) => {
                                            setBookingBlacklistForm((current) => ({
                                                ...current,
                                                email: event.target.value
                                            }));
                                            clearFieldError('bookingBlacklist', 'email');
                                        }}
                                        placeholder="blocked@example.com"
                                    />
                                    {fieldErrors.bookingBlacklist?.email ? (
                                        <small>{fieldErrors.bookingBlacklist.email}</small>
                                    ) : null}
                                </label>

                                <label className="booking-form-field">
                                    <span>
                                        <Phone size={15} />
                                        Phone
                                    </span>
                                    <input
                                        className="payment-input"
                                        value={bookingBlacklistForm.phone}
                                        onChange={(event) => {
                                            setBookingBlacklistForm((current) => ({
                                                ...current,
                                                phone: event.target.value
                                            }));
                                            clearFieldError('bookingBlacklist', 'phone');
                                        }}
                                        placeholder="+353 87 000 0000"
                                    />
                                    {fieldErrors.bookingBlacklist?.phone ? (
                                        <small>{fieldErrors.bookingBlacklist.phone}</small>
                                    ) : null}
                                </label>

                                <label className="booking-form-field">
                                    <span>
                                        <ShieldAlert size={15} />
                                        Reason
                                    </span>
                                    <textarea
                                        className="payment-input admin-textarea"
                                        rows={4}
                                        value={bookingBlacklistForm.reason}
                                        onChange={(event) => {
                                            setBookingBlacklistForm((current) => ({
                                                ...current,
                                                reason: event.target.value
                                            }));
                                            clearFieldError('bookingBlacklist', 'reason');
                                        }}
                                        placeholder="Repeated no-shows, abusive behavior, chargeback fraud..."
                                    />
                                    {fieldErrors.bookingBlacklist?.reason ? (
                                        <small>{fieldErrors.bookingBlacklist.reason}</small>
                                    ) : null}
                                </label>
                            </div>

                            <div className="row admin-card-actions admin-blacklist-actions">
                                <GoldButton
                                    type="button"
                                    onClick={createBookingBlacklistEntry}
                                    disabled={
                                        loading ||
                                        (!bookingBlacklistForm.email.trim() &&
                                            !bookingBlacklistForm.phone.trim())
                                    }
                                >
                                    Save Blacklist Entry
                                </GoldButton>
                            </div>
                        </div>
                    </LuxuryCard>

                    <LuxuryCard className="admin-blacklist-list-card">
                        <div className="admin-card-heading">
                            <div className="booking-summary-badge">Active Restrictions</div>
                        </div>
                        <div className="admin-card-body admin-blacklist-list-body">
                            <div className="row admin-card-actions admin-blacklist-toolbar">
                                <button
                                    type="button"
                                    className="btn-gold"
                                    onClick={refreshBookingBlacklist}
                                    disabled={loading}
                                >
                                    Refresh Blacklist
                                </button>
                            </div>

                            <div className="table-wrapper admin-table-shell admin-table-shell-wide">
                                <table className="admin-data-table admin-data-table-blacklist">
                                    <thead>
                                        <tr>
                                            <th>Email</th>
                                            <th>Phone</th>
                                            <th>Reason</th>
                                            <th>Added</th>
                                            <th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {bookingBlacklistEntries.length === 0 ? (
                                            <tr>
                                                <td colSpan={5}>Blacklist is empty.</td>
                                            </tr>
                                        ) : (
                                            bookingBlacklistEntries.map((entry) => (
                                                <tr key={entry.id}>
                                                    <td>{entry.email || '-'}</td>
                                                    <td>{entry.phone || '-'}</td>
                                                    <td>{entry.reason || '-'}</td>
                                                    <td>{formatTimestamp(entry.createdAt)}</td>
                                                    <td className="row admin-table-actions">
                                                        <button
                                                            type="button"
                                                            className="btn-gold admin-inline-btn"
                                                            onClick={() =>
                                                                deleteBookingBlacklistEntry(
                                                                    entry.id
                                                                )
                                                            }
                                                            disabled={loading}
                                                        >
                                                            Remove
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </LuxuryCard>
                </div>

                {sectionErrors.bookingBlacklist ? (
                    <p className="section-error">{sectionErrors.bookingBlacklist}</p>
                ) : null}
                {sectionSuccess.bookingBlacklist ? (
                    <p className="section-ok">{sectionSuccess.bookingBlacklist}</p>
                ) : null}
            </section>
        </div>
    );
}
