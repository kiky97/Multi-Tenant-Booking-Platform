import { useState } from 'react';
import SectionTitle from '../../components/ui/SectionTitle';

function scrollToAdminSection(sectionId) {
    if (typeof document === 'undefined') {
        return;
    }

    document.getElementById(sectionId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
    });
}

export default function EmployeesPage({
    employeeForm,
    setEmployeeForm,
    sortedEmployees,
    sortedBookableEmployees,
    sortedTreatments,
    selectedEmployeeId,
    setSelectedEmployeeId,
    selectedDate,
    setSelectedDate,
    employeeDay,
    setEmployeeDay,
    periodTargetEmployeeId,
    setPeriodTargetEmployeeId,
    periodStartDate,
    setPeriodStartDate,
    periodEndDate,
    setPeriodEndDate,
    employeePeriodDays,
    periodTemplateSourceEmployeeId,
    setPeriodTemplateSourceEmployeeId,
    periodTemplateWeekStart,
    setPeriodTemplateWeekStart,
    updateEmployeePeriodDay,
    loading,
    clearFieldError,
    fieldErrors,
    sectionErrors,
    sectionSuccess,
    createEmployee,
    updateEmployee,
    clearEmployeeForm,
    fillEmployeeForm,
    deleteEmployee,
    swapEmployees,
    updateEmployeePeriod,
    applyEmployeePeriodSalonDefaults,
    copyEmployeePeriodFromEmployee,
    repeatSelectedWeekPattern,
    updateEmployeeDay,
    formatDayLabel,
    allEmployeesOption
}) {
    const [draggedEmployeeId, setDraggedEmployeeId] = useState('');
    const treatmentNameById = new Map(sortedTreatments.map((treatment) => [treatment.id, treatment.name]));

    function toggleTreatment(treatmentId) {
        setEmployeeForm((current) => {
            const treatmentIds = Array.isArray(current.treatmentIds) ? current.treatmentIds : [];
            const hasTreatment = treatmentIds.includes(treatmentId);

            return {
                ...current,
                treatmentIds: hasTreatment
                    ? treatmentIds.filter((id) => id !== treatmentId)
                    : [...treatmentIds, treatmentId]
            };
        });
        clearFieldError('employees', 'treatmentIds');
    }

    function selectAllTreatments() {
        setEmployeeForm((current) => ({
            ...current,
            treatmentIds: sortedTreatments.map((treatment) => treatment.id)
        }));
        clearFieldError('employees', 'treatmentIds');
    }

    function clearAllTreatments() {
        setEmployeeForm((current) => ({
            ...current,
            treatmentIds: []
        }));
        clearFieldError('employees', 'treatmentIds');
    }

    function renderEmployeeServices(item) {
        const names = (item.treatmentIds || [])
            .map((id) => treatmentNameById.get(id))
            .filter(Boolean);

        return names.length ? names.join(', ') : 'None';
    }

    async function handleEmployeeSwap(firstEmployeeId, secondEmployeeId) {
        if (!firstEmployeeId || !secondEmployeeId || firstEmployeeId === secondEmployeeId) {
            return;
        }

        setDraggedEmployeeId('');
        await swapEmployees(firstEmployeeId, secondEmployeeId);
    }

    const employeeSections = [
        { id: 'admin-employees-list', label: 'Employee List' },
        { id: 'admin-employees-reorder', label: 'Reorder' },
        { id: 'admin-employees-period', label: 'Per Period' },
        { id: 'admin-employees-date', label: 'Per Date' }
    ];

    return (
        <div className="admin-page-stack">
            <section className="panel">
                <SectionTitle title="Employees" subtitle="Admin Employees Workspace" />
                <p className="panel-note">
                    Manage the team profile, edit existing employees, and control schedule logic from one dedicated workspace.
                </p>

                <div className="admin-subsection-nav">
                    {employeeSections.map((section) => (
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

            <section id="admin-employees-workspace" className="panel admin-workspace-section">
                <SectionTitle title="Team Management" subtitle="Employees" />

                <form className="grid two" onSubmit={(event) => event.preventDefault()}>
                    {employeeForm.id ? (
                        <label className="full">
                            Employee ID
                            <input
                                className="payment-input admin-readonly-input"
                                value={employeeForm.id}
                                readOnly
                            />
                        </label>
                    ) : null}

                    <label>
                        Name
                        <input
                            className="payment-input"
                            value={employeeForm.name}
                            onChange={(event) => {
                                setEmployeeForm((current) => ({ ...current, name: event.target.value }));
                                clearFieldError('employees', 'name');
                            }}
                        />
                        {fieldErrors.employees?.name ? <span className="field-error">{fieldErrors.employees.name}</span> : null}
                    </label>

                    <div className="employee-inline-row">
                        <label>
                            Role
                            <input
                                className="payment-input"
                                value={employeeForm.role}
                                onChange={(event) => {
                                    setEmployeeForm((current) => ({ ...current, role: event.target.value }));
                                    clearFieldError('employees', 'role');
                                }}
                            />
                            {fieldErrors.employees?.role ? (
                                <span className="field-error">{fieldErrors.employees.role}</span>
                            ) : null}
                        </label>

                        <label className="employee-bookable-field">
                            <span>Is Barber</span>
                            <select
                                className="admin-choice-select employee-bookable-select"
                                value={employeeForm.bookable ? 'true' : 'false'}
                                onChange={(event) =>
                                    setEmployeeForm((current) => ({
                                        ...current,
                                        bookable: event.target.value === 'true'
                                    }))
                                }
                            >
                                <option value="true">true</option>
                                <option value="false">false</option>
                            </select>
                        </label>
                    </div>

                    <label className="full">
                        Bio
                        <textarea
                            className="payment-input admin-textarea"
                            rows={3}
                            value={employeeForm.bio}
                            onChange={(event) => setEmployeeForm((current) => ({ ...current, bio: event.target.value }))}
                        />
                    </label>

                    <label>
                        Photo URL
                        <input
                            className="payment-input"
                            value={employeeForm.photoUrl}
                            onChange={(event) => setEmployeeForm((current) => ({ ...current, photoUrl: event.target.value }))}
                        />
                    </label>

                    <div className="full employee-services-field">
                        <div className="employee-services-field-header">
                            <div className="employee-services-field-copy">
                                <span>Provides Services</span>
                                <small>Select every treatment this employee can perform.</small>
                            </div>
                            <div className="employee-services-toolbar">
                                <button
                                    type="button"
                                    className="btn-gold admin-inline-btn"
                                    onClick={selectAllTreatments}
                                    disabled={loading || sortedTreatments.length === 0}
                                >
                                    Select All
                                </button>
                                <button
                                    type="button"
                                    className="btn-gold admin-inline-btn"
                                    onClick={clearAllTreatments}
                                    disabled={loading || (employeeForm.treatmentIds || []).length === 0}
                                >
                                    Clear All
                                </button>
                            </div>
                        </div>

                        <div className="employee-services-grid">
                            {sortedTreatments.map((treatment) => (
                                <label key={treatment.id} className="employee-service-option">
                                    <input
                                        type="checkbox"
                                        checked={(employeeForm.treatmentIds || []).includes(treatment.id)}
                                        onChange={() => toggleTreatment(treatment.id)}
                                    />
                                    <span>{treatment.name}</span>
                                </label>
                            ))}
                        </div>

                        {sortedTreatments.length === 0 ? (
                            <span className="panel-note">Create at least one treatment to assign services to employees.</span>
                        ) : null}
                        {fieldErrors.employees?.treatmentIds ? (
                            <span className="field-error">{fieldErrors.employees.treatmentIds}</span>
                        ) : null}
                    </div>
                </form>

                <div className="row admin-form-actions">
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={createEmployee}
                        disabled={loading || Boolean(employeeForm.id)}
                    >
                        Create
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={updateEmployee}
                        disabled={loading || !employeeForm.id}
                    >
                        Update
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={clearEmployeeForm}
                        disabled={loading}
                    >
                        Clear
                    </button>
                </div>

                {sectionErrors.employees ? <p className="section-error">{sectionErrors.employees}</p> : null}
                {sectionSuccess.employees ? <p className="section-ok">{sectionSuccess.employees}</p> : null}
            </section>

            <section id="admin-employees-list" className="panel admin-workspace-section">
                <SectionTitle title="Employee List" subtitle="Edit Existing Team" />

                <div className="table-wrapper admin-table-shell">
                    <table className="admin-data-table admin-data-table-team">
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Role</th>
                                <th>Provides Services</th>
                                <th>Order</th>
                                <th>Photo URL</th>
                                <th>ID</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedEmployees.map((item) => (
                                <tr key={item.id}>
                                    <td>{item.name}</td>
                                    <td>{item.role}</td>
                                    <td className="employee-services-summary">{renderEmployeeServices(item)}</td>
                                    <td>{item.displayOrder}</td>
                                    <td>{item.photoUrl || '-'}</td>
                                    <td className="code">{item.id}</td>
                                    <td className="row admin-table-actions">
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => {
                                                fillEmployeeForm(item);
                                                scrollToAdminSection('admin-employees-workspace');
                                            }}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => deleteEmployee(item.id)}
                                            disabled={loading}
                                        >
                                            Delete
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </section>

            <section id="admin-employees-reorder" className="panel admin-workspace-section">
                <SectionTitle title="Reorder Employees" subtitle="Drag, Drop, Or Tap" />
                <p className="panel-note">
                    On desktop, drag one employee card onto another to swap their positions. On mobile, use the Up and Down buttons.
                </p>

                <div className="admin-reorder-stack">
                    {sortedEmployees.map((item, index) => (
                        <div
                            key={item.id}
                            className={`admin-reorder-card ${draggedEmployeeId === item.id ? 'admin-reorder-card-dragging' : ''}`}
                            draggable={!loading}
                            onDragStart={() => setDraggedEmployeeId(item.id)}
                            onDragEnd={() => setDraggedEmployeeId('')}
                            onDragOver={(event) => event.preventDefault()}
                            onDrop={() => handleEmployeeSwap(draggedEmployeeId, item.id)}
                        >
                            <div className="admin-reorder-card-main">
                                <div className="admin-reorder-badge">#{item.displayOrder}</div>
                                <div className="admin-reorder-copy">
                                    <strong>{item.name}</strong>
                                    <span>
                                        {item.role || 'No role set'}
                                        {item.bookable ? ' · Visible in booking' : ' · Hidden from booking'}
                                    </span>
                                </div>
                            </div>

                            <div className="admin-reorder-actions">
                                <button
                                    type="button"
                                    className="btn-gold admin-inline-btn"
                                    onClick={() => handleEmployeeSwap(item.id, sortedEmployees[index - 1]?.id)}
                                    disabled={loading || index === 0}
                                >
                                    Up
                                </button>
                                <button
                                    type="button"
                                    className="btn-gold admin-inline-btn"
                                    onClick={() => handleEmployeeSwap(item.id, sortedEmployees[index + 1]?.id)}
                                    disabled={loading || index === sortedEmployees.length - 1}
                                >
                                    Down
                                </button>
                            </div>
                        </div>
                    ))}
                </div>

                {sectionErrors.reorder ? <p className="section-error">{sectionErrors.reorder}</p> : null}
                {sectionSuccess.reorder ? <p className="section-ok">{sectionSuccess.reorder}</p> : null}
            </section>

            <section id="admin-employees-period" className="panel admin-workspace-section">
                <SectionTitle title="Employee Schedule (per period)" subtitle="Bulk Schedule Updates" />

                <div className="admin-template-panel">
                    <div className="admin-template-header">
                        <h3>Schedule Templates</h3>
                        <p className="panel-note">
                            Load salon defaults, copy a saved week from another employee, or reuse the week currently open in the per-date editor.
                        </p>
                    </div>

                    <div className="admin-template-grid">
                        <button
                            type="button"
                            className="btn-gold admin-template-grid-wide"
                            onClick={applyEmployeePeriodSalonDefaults}
                            disabled={loading}
                        >
                            Use Salon Default Hours
                        </button>

                        <label className="admin-control-field">
                            <span>Source Employee</span>
                            <select
                                value={periodTemplateSourceEmployeeId}
                                onChange={(event) => setPeriodTemplateSourceEmployeeId(event.target.value)}
                            >
                                <option value="">Select employee</option>
                                {sortedBookableEmployees.map((item) => (
                                    <option key={item.id} value={item.id}>
                                        {item.name}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className="admin-control-field">
                            <span>Week Start</span>
                            <input
                                type="date"
                                value={periodTemplateWeekStart}
                                onChange={(event) => setPeriodTemplateWeekStart(event.target.value)}
                            />
                        </label>

                        <button
                            type="button"
                            className="btn-gold admin-template-grid-wide"
                            onClick={copyEmployeePeriodFromEmployee}
                            disabled={loading || !periodTemplateSourceEmployeeId}
                        >
                            Copy From Employee
                        </button>

                        <button
                            type="button"
                            className="btn-gold admin-template-grid-wide"
                            onClick={repeatSelectedWeekPattern}
                            disabled={loading || !selectedEmployeeId}
                        >
                            Repeat This Week Pattern
                        </button>
                    </div>
                </div>

                <div className="row admin-control-row admin-control-row-schedule">
                    <label className="admin-control-field">
                        <span>Apply To</span>
                        <select value={periodTargetEmployeeId} onChange={(event) => setPeriodTargetEmployeeId(event.target.value)}>
                            <option value={allEmployeesOption}>All employees</option>
                            {sortedBookableEmployees.map((item) => (
                                <option key={item.id} value={item.id}>
                                    {item.name}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label className="admin-control-field">
                        <span>Start Date</span>
                        <input
                            type="date"
                            value={periodStartDate}
                            max={periodEndDate || undefined}
                            onChange={(event) => setPeriodStartDate(event.target.value)}
                        />
                    </label>

                    <label className="admin-control-field">
                        <span>End Date</span>
                        <input
                            type="date"
                            value={periodEndDate}
                            min={periodStartDate || undefined}
                            onChange={(event) => setPeriodEndDate(event.target.value)}
                        />
                    </label>
                </div>

                <div className="table-wrapper admin-table-shell">
                    <table className="admin-data-table admin-data-table-schedule">
                        <thead>
                            <tr>
                                <th>Day</th>
                                <th>Working</th>
                                <th>Open</th>
                                <th>Close</th>
                                <th>Break Start</th>
                                <th>Break End</th>
                            </tr>
                        </thead>
                        <tbody>
                            {employeePeriodDays.map((row) => (
                                <tr key={row.dayOfWeek}>
                                    <td>{formatDayLabel(row.dayOfWeek)}</td>
                                    <td>
                                        <input
                                            type="checkbox"
                                            checked={row.workingDay}
                                            onChange={(event) => updateEmployeePeriodDay(row.dayOfWeek, { workingDay: event.target.checked })}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.openTime}
                                            onChange={(event) => updateEmployeePeriodDay(row.dayOfWeek, { openTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.closeTime}
                                            onChange={(event) => updateEmployeePeriodDay(row.dayOfWeek, { closeTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.breakStartTime}
                                            onChange={(event) => updateEmployeePeriodDay(row.dayOfWeek, { breakStartTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.breakEndTime}
                                            onChange={(event) => updateEmployeePeriodDay(row.dayOfWeek, { breakEndTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <div className="row admin-section-actions">
                    <button type="button" className="btn-gold" onClick={updateEmployeePeriod} disabled={loading || sortedEmployees.length === 0}>
                        Update Period
                    </button>
                </div>

                {sectionErrors.employeePeriodHours ? <p className="section-error">{sectionErrors.employeePeriodHours}</p> : null}
                {sectionSuccess.employeePeriodHours ? <p className="section-ok">{sectionSuccess.employeePeriodHours}</p> : null}
            </section>

            <section id="admin-employees-date" className="panel admin-workspace-section">
                <SectionTitle title="Employee Schedule (per date)" subtitle="Single-Day Override" />

                <div className="row admin-control-row admin-control-row-schedule">
                    <label className="admin-control-field">
                        <span>Employee</span>
                        <select value={selectedEmployeeId} onChange={(event) => setSelectedEmployeeId(event.target.value)}>
                            <option value="">Select employee</option>
                            {sortedBookableEmployees.map((item) => (
                                <option key={item.id} value={item.id}>
                                    {item.name}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label className="admin-control-field">
                        <span>Working Date</span>
                        <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
                    </label>
                </div>

                <div className="grid two employee-day-grid admin-form-grid-shell">
                    <label className="employee-toggle-field">
                        Working Day
                        <input
                            type="checkbox"
                            checked={employeeDay.workingDay}
                            onChange={(event) => setEmployeeDay((current) => ({ ...current, workingDay: event.target.checked }))}
                        />
                    </label>

                    <div className="employee-day-grid-spacer" aria-hidden="true" />

                    <label>
                        Open Time
                        <input
                            type="time"
                            value={employeeDay.openTime}
                            onChange={(event) => setEmployeeDay((current) => ({ ...current, openTime: event.target.value }))}
                            disabled={!employeeDay.workingDay}
                        />
                    </label>

                    <label>
                        Close Time
                        <input
                            type="time"
                            value={employeeDay.closeTime}
                            onChange={(event) => setEmployeeDay((current) => ({ ...current, closeTime: event.target.value }))}
                            disabled={!employeeDay.workingDay}
                        />
                    </label>

                    <label>
                        Break Start
                        <input
                            type="time"
                            value={employeeDay.breakStartTime}
                            onChange={(event) => setEmployeeDay((current) => ({ ...current, breakStartTime: event.target.value }))}
                            disabled={!employeeDay.workingDay}
                        />
                    </label>

                    <label>
                        Break End
                        <input
                            type="time"
                            value={employeeDay.breakEndTime}
                            onChange={(event) => setEmployeeDay((current) => ({ ...current, breakEndTime: event.target.value }))}
                            disabled={!employeeDay.workingDay}
                        />
                    </label>
                </div>

                <div className="row admin-section-actions">
                    <button type="button" className="btn-gold" onClick={updateEmployeeDay} disabled={loading || !selectedEmployeeId}>
                        Update Day
                    </button>
                </div>

                {sectionErrors.employeeHours ? <p className="section-error">{sectionErrors.employeeHours}</p> : null}
                {sectionSuccess.employeeHours ? <p className="section-ok">{sectionSuccess.employeeHours}</p> : null}
            </section>
        </div>
    );
}
