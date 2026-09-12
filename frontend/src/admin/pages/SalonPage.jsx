import SectionTitle from '../../components/ui/SectionTitle';

export default function SalonPage({
    hairSalonForm,
    setHairSalonForm,
    hairSalonHours,
    setHairSalonHours,
    clearFieldError,
    fieldErrors,
    loading,
    sectionErrors,
    sectionSuccess,
    updateHairSalon,
    updateSalonDay,
    copyMondayHoursToWeekdays,
    saveAllSalonHours
}) {
    return (
        <div className="admin-page-stack">
            <section className="panel">
                <SectionTitle title="Salon Details" subtitle="Public Presentation" />

                <form className="grid two" onSubmit={(event) => event.preventDefault()}>
                    <label>
                        Name
                        <input
                            className="payment-input"
                            value={hairSalonForm.name}
                            onChange={(event) => {
                                setHairSalonForm((current) => ({ ...current, name: event.target.value }));
                                clearFieldError('hairSalon', 'name');
                            }}
                        />
                        {fieldErrors.hairSalon?.name ? <span className="field-error">{fieldErrors.hairSalon.name}</span> : null}
                    </label>

                    <label>
                        Email
                        <input
                            className="payment-input"
                            value={hairSalonForm.email}
                            onChange={(event) => {
                                setHairSalonForm((current) => ({ ...current, email: event.target.value }));
                                clearFieldError('hairSalon', 'email');
                            }}
                        />
                        {fieldErrors.hairSalon?.email ? <span className="field-error">{fieldErrors.hairSalon.email}</span> : null}
                    </label>

                    <label>
                        Phone
                        <input
                            className="payment-input"
                            value={hairSalonForm.phone}
                            onChange={(event) => setHairSalonForm((current) => ({ ...current, phone: event.target.value }))}
                        />
                    </label>

                    <label>
                        Address
                        <input
                            className="payment-input"
                            value={hairSalonForm.address}
                            onChange={(event) => {
                                setHairSalonForm((current) => ({ ...current, address: event.target.value }));
                                clearFieldError('hairSalon', 'address');
                            }}
                        />
                        {fieldErrors.hairSalon?.address ? (
                            <span className="field-error">{fieldErrors.hairSalon.address}</span>
                        ) : null}
                    </label>

                    <label className="full">
                        Description
                        <textarea
                            className="payment-input admin-textarea"
                            rows={4}
                            value={hairSalonForm.description}
                            onChange={(event) => setHairSalonForm((current) => ({ ...current, description: event.target.value }))}
                        />
                    </label>
                </form>

                <div className="row admin-form-actions">
                    <button type="button" className="btn-gold" onClick={updateHairSalon} disabled={loading}>
                        Save Salon Details
                    </button>
                </div>

                {sectionErrors.hairSalon ? <p className="section-error">{sectionErrors.hairSalon}</p> : null}
                {sectionSuccess.hairSalon ? <p className="section-ok">{sectionSuccess.hairSalon}</p> : null}
            </section>

            <section className="panel">
                <SectionTitle title="Salon Working Hours" subtitle="Weekly Schedule" />
                <p className="panel-note">
                    Update individual days when needed, or use the bulk actions to roll Monday across weekdays and save every changed row in one pass.
                </p>

                <div className="row admin-form-actions admin-hours-toolbar">
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={copyMondayHoursToWeekdays}
                        disabled={loading || hairSalonHours.length === 0}
                    >
                        Copy Monday To Weekdays
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={saveAllSalonHours}
                        disabled={loading || hairSalonHours.length === 0}
                    >
                        Save All Changed Rows
                    </button>
                </div>

                <div className="table-wrapper admin-table-shell">
                    <table className="admin-data-table admin-data-table-schedule">
                        <thead>
                            <tr>
                                <th>Day</th>
                                <th>Working</th>
                                <th>Open</th>
                                <th>Close</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            {hairSalonHours.map((row, index) => (
                                <tr key={row.dayOfWeek}>
                                    <td>{row.dayOfWeek}</td>
                                    <td>
                                        <input
                                            type="checkbox"
                                            checked={row.workingDay}
                                            onChange={(event) =>
                                                setHairSalonHours((previous) =>
                                                    previous.map((item, itemIndex) =>
                                                        itemIndex === index ? { ...item, workingDay: event.target.checked } : item
                                                    )
                                                )
                                            }
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.openTime}
                                            onChange={(event) =>
                                                setHairSalonHours((previous) =>
                                                    previous.map((item, itemIndex) =>
                                                        itemIndex === index ? { ...item, openTime: event.target.value } : item
                                                    )
                                                )
                                            }
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.closeTime}
                                            onChange={(event) =>
                                                setHairSalonHours((previous) =>
                                                    previous.map((item, itemIndex) =>
                                                        itemIndex === index ? { ...item, closeTime: event.target.value } : item
                                                    )
                                                )
                                            }
                                        />
                                    </td>
                                    <td>
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => updateSalonDay(row)}
                                            disabled={loading}
                                        >
                                            Save
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                {sectionErrors.hairSalonHours ? <p className="section-error">{sectionErrors.hairSalonHours}</p> : null}
                {sectionSuccess.hairSalonHours ? <p className="section-ok">{sectionSuccess.hairSalonHours}</p> : null}
            </section>
        </div>
    );
}
