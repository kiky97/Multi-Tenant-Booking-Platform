import LuxuryCard from '../ui/LuxuryCard';

function formatSlotLabel(slot) {
    return `${slot.startTime.slice(0, 5)} - ${slot.endTime.slice(0, 5)}`;
}

function formatSlotState(slot) {
    if (slot.status === 'BREAK') {
        return 'Break';
    }

    if (slot.status === 'HELD') {
        return 'Held';
    }

    if (slot.status === 'BOOKED') {
        return 'Booked';
    }

    return '';
}

export default function TimeSlotList({
    slots = [],
    selectedSlot = null,
    onSelect,
    loading = false,
    emptyMessage = 'No available time slots for this date.'
}) {
    const selectedKey = selectedSlot?.startTime || null;

    return (
        <LuxuryCard className="booking-time-slot-card p-4 space-y-3">
            <div className="text-[12px] uppercase tracking-[0.18em] text-smoke">Select the time</div>

            {loading && slots.length === 0 ? <div className="text-sm text-smoke">Loading available slots...</div> : null}

            {!loading && slots.length === 0 ? (
                <div className="text-sm text-smoke">{emptyMessage}</div>
            ) : null}

            {slots.length > 0 ? (
                <div className="time-slot-list-grid">
                    {slots.map((slot) => {
                        const isSelected = selectedKey === slot.startTime;
                        const stateLabel = formatSlotState(slot);

                        return (
                            <button
                                key={`${slot.startTime}-${slot.endTime}`}
                                type="button"
                                className={`time-slot ${slot.available ? '' : 'booked'} ${slot.status === 'BREAK' ? 'time-slot-break' : ''
                                    } ${slot.status === 'HELD' ? 'time-slot-held' : ''
                                    } ${isSelected ? 'time-slot-selected' : ''
                                    }`}
                                disabled={!slot.available || loading}
                                onClick={() => onSelect(slot)}
                                aria-pressed={isSelected}
                            >
                                <span className="time-slot-primary">
                                    <span className="time-slot-label">{formatSlotLabel(slot)}</span>
                                    {isSelected ? <span className="time-slot-selected-badge">Selected</span> : null}
                                </span>
                                <span
                                    className={`time-slot-status ${stateLabel ? '' : 'time-slot-status-placeholder'}`}
                                    aria-hidden={!stateLabel}
                                >
                                    {stateLabel || '\u00A0'}
                                </span>
                            </button>
                        );
                    })}
                </div>
            ) : null}
        </LuxuryCard>
    );
}
