import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import LuxuryCard from '../ui/LuxuryCard';

const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function startOfDay(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function isSameDay(left, right) {
    return left.getFullYear() === right.getFullYear()
        && left.getMonth() === right.getMonth()
        && left.getDate() === right.getDate();
}

function toDateKey(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function buildMonth(year, month) {
    const firstDay = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const days = [];

    for (let index = 0; index < firstDay; index += 1) {
        days.push(null);
    }

    for (let dayNumber = 1; dayNumber <= daysInMonth; dayNumber += 1) {
        days.push({
            date: new Date(year, month, dayNumber),
            dayNumber
        });
    }

    const trailingDays = (7 - (days.length % 7)) % 7;

    for (let index = 0; index < trailingDays; index += 1) {
        days.push(null);
    }

    return days;
}

export default function BookingCalendar({ selectedDate, onSelect }) {
    const [viewDate, setViewDate] = useState(
        () => new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1)
    );

    useEffect(() => {
        setViewDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1));
    }, [selectedDate]);

    const [year, month] = [viewDate.getFullYear(), viewDate.getMonth()];
    const monthDays = buildMonth(year, month);
    const title = viewDate.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

    const changeMonth = (delta) => {
        setViewDate(new Date(year, month + delta, 1));
    };

    return (
        <LuxuryCard className="booking-calendar-shell p-4">
            <div className="calendar-header">
                <button
                    type="button"
                    className="calendar-nav-button"
                    onClick={() => changeMonth(-1)}
                    aria-label="Previous month"
                >
                    <ChevronLeft size={18} />
                </button>
                <div className="calendar-title">{title}</div>
                <button
                    type="button"
                    className="calendar-nav-button"
                    onClick={() => changeMonth(1)}
                    aria-label="Next month"
                >
                    <ChevronRight size={18} />
                </button>
            </div>

            <div className="calendar-weekdays">
                {weekday.map((day) => (
                    <div key={day} className="calendar-weekday">
                        {day}
                    </div>
                ))}
            </div>

            <div className="calendar-grid">
                {monthDays.map((cell, index) => {
                    if (!cell) {
                        return <div key={`calendar-placeholder-${year}-${month}-${index}`} className="calendar-day-placeholder" aria-hidden="true" />;
                    }

                    const isActive = isSameDay(cell.date, selectedDate);
                    const isPast = startOfDay(cell.date) < startOfDay(new Date());
                    return (
                        <button
                            key={toDateKey(cell.date)}
                            type="button"
                            aria-label={cell.date.toLocaleDateString('en-US', {
                                weekday: 'long',
                                month: 'long',
                                day: 'numeric',
                                year: 'numeric'
                            })}
                            className={`calendar-day ${isActive ? 'active' : ''} ${isPast ? 'muted' : ''}`}
                            disabled={isPast}
                            onClick={() => onSelect(new Date(cell.date))}
                        >
                            {cell.dayNumber}
                        </button>
                    );
                })}
            </div>
        </LuxuryCard>
    );
}
