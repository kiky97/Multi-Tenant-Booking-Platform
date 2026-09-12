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

export default function TreatmentsPage({
    treatmentForm,
    setTreatmentForm,
    sortedTreatments,
    loading,
    clearFieldError,
    fieldErrors,
    sectionErrors,
    sectionSuccess,
    createTreatment,
    updateTreatment,
    clearTreatmentForm,
    fillTreatmentForm,
    deleteTreatment,
    swapTreatments
}) {
    const [draggedTreatmentId, setDraggedTreatmentId] = useState('');

    async function handleTreatmentSwap(firstTreatmentId, secondTreatmentId) {
        if (!firstTreatmentId || !secondTreatmentId || firstTreatmentId === secondTreatmentId) {
            return;
        }

        setDraggedTreatmentId('');
        await swapTreatments(firstTreatmentId, secondTreatmentId);
    }

    const treatmentSections = [
        { id: 'admin-treatments-list', label: 'Treatments List' },
        { id: 'admin-treatments-reorder', label: 'Reorder' }
    ];

    return (
        <div className="admin-page-stack">
            <section className="panel">
                <SectionTitle title="Treatments" subtitle="Admin Treatments Workspace" />
                <p className="panel-note">
                    Control the service catalog, jump between editing sections quickly, and keep every treatment in one consistent admin workspace.
                </p>

                <div className="admin-subsection-nav">
                    {treatmentSections.map((section) => (
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

            <section id="admin-treatments-workspace" className="panel admin-workspace-section">
                <SectionTitle title="Service Catalog" subtitle="Treatments" />

                <form className="grid two" onSubmit={(event) => event.preventDefault()}>
                    {treatmentForm.id ? (
                        <label className="full">
                            Treatment ID
                            <input
                                className="payment-input admin-readonly-input"
                                value={treatmentForm.id}
                                readOnly
                            />
                        </label>
                    ) : null}

                    <label>
                        Name
                        <input
                            className="payment-input"
                            value={treatmentForm.name}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, name: event.target.value }));
                                clearFieldError('treatments', 'name');
                            }}
                        />
                        {fieldErrors.treatments?.name ? <span className="field-error">{fieldErrors.treatments.name}</span> : null}
                    </label>

                    <label>
                        Duration (minutes)
                        <input
                            className="payment-input"
                            type="number"
                            value={treatmentForm.durationMinutes}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, durationMinutes: event.target.value }));
                                clearFieldError('treatments', 'durationMinutes');
                            }}
                        />
                        {fieldErrors.treatments?.durationMinutes ? (
                            <span className="field-error">{fieldErrors.treatments.durationMinutes}</span>
                        ) : null}
                    </label>

                    <label>
                        Price (EUR)
                        <input
                            className="payment-input"
                            type="number"
                            step="0.01"
                            value={treatmentForm.price}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, price: event.target.value }));
                                clearFieldError('treatments', 'price');
                            }}
                        />
                        {fieldErrors.treatments?.price ? <span className="field-error">{fieldErrors.treatments.price}</span> : null}
                    </label>

                    <label>
                        Photo URL
                        <input
                            className="payment-input"
                            value={treatmentForm.photoUrl}
                            onChange={(event) => setTreatmentForm((current) => ({ ...current, photoUrl: event.target.value }))}
                        />
                    </label>

                    <label className="full">
                        Description
                        <textarea
                            className="payment-input admin-textarea"
                            rows={4}
                            value={treatmentForm.description}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, description: event.target.value }));
                                clearFieldError('treatments', 'description');
                            }}
                        />
                        {fieldErrors.treatments?.description ? (
                            <span className="field-error">{fieldErrors.treatments.description}</span>
                        ) : null}
                    </label>
                </form>

                <div className="row admin-form-actions">
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={createTreatment}
                        disabled={loading || Boolean(treatmentForm.id)}
                    >
                        Create
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={updateTreatment}
                        disabled={loading || !treatmentForm.id}
                    >
                        Update
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={clearTreatmentForm}
                        disabled={loading}
                    >
                        Clear
                    </button>
                </div>

                {sectionErrors.treatments ? <p className="section-error">{sectionErrors.treatments}</p> : null}
                {sectionSuccess.treatments ? <p className="section-ok">{sectionSuccess.treatments}</p> : null}
            </section>

            <section id="admin-treatments-list" className="panel admin-workspace-section">
                <SectionTitle title="Treatments List" subtitle="Current Order" />

                <div className="table-wrapper">
                    <table>
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Order</th>
                                <th>Duration</th>
                                <th>Price</th>
                                <th>Photo URL</th>
                                <th>ID</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedTreatments.map((item) => (
                                <tr key={item.id}>
                                    <td>{item.name}</td>
                                    <td>{item.displayOrder}</td>
                                    <td>{item.durationMinutes}</td>
                                    <td>{item.price}</td>
                                    <td>{item.photoUrl || '-'}</td>
                                    <td className="code">{item.id}</td>
                                    <td className="row admin-table-actions">
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => {
                                                fillTreatmentForm(item);
                                                scrollToAdminSection('admin-treatments-workspace');
                                            }}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => deleteTreatment(item.id)}
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

            <section id="admin-treatments-reorder" className="panel admin-workspace-section">
                <SectionTitle title="Reorder Treatments" subtitle="Drag, Drop, Or Tap" />
                <p className="panel-note">
                    On desktop, drag one treatment card onto another to swap positions. On mobile, use the Up and Down buttons.
                </p>

                <div className="admin-reorder-stack">
                    {sortedTreatments.map((item, index) => (
                        <div
                            key={item.id}
                            className={`admin-reorder-card ${draggedTreatmentId === item.id ? 'admin-reorder-card-dragging' : ''}`}
                            draggable={!loading}
                            onDragStart={() => setDraggedTreatmentId(item.id)}
                            onDragEnd={() => setDraggedTreatmentId('')}
                            onDragOver={(event) => event.preventDefault()}
                            onDrop={() => handleTreatmentSwap(draggedTreatmentId, item.id)}
                        >
                            <div className="admin-reorder-card-main">
                                <div className="admin-reorder-badge">#{item.displayOrder}</div>
                                <div className="admin-reorder-copy">
                                    <strong>{item.name}</strong>
                                    <span>
                                        {item.durationMinutes} min · {item.price} EUR
                                    </span>
                                </div>
                            </div>

                            <div className="admin-reorder-actions">
                                <button
                                    type="button"
                                    className="btn-gold admin-inline-btn"
                                    onClick={() => handleTreatmentSwap(item.id, sortedTreatments[index - 1]?.id)}
                                    disabled={loading || index === 0}
                                >
                                    Up
                                </button>
                                <button
                                    type="button"
                                    className="btn-gold admin-inline-btn"
                                    onClick={() => handleTreatmentSwap(item.id, sortedTreatments[index + 1]?.id)}
                                    disabled={loading || index === sortedTreatments.length - 1}
                                >
                                    Down
                                </button>
                            </div>
                        </div>
                    ))}
                </div>

                {sectionErrors.treatmentReorder ? <p className="section-error">{sectionErrors.treatmentReorder}</p> : null}
                {sectionSuccess.treatmentReorder ? <p className="section-ok">{sectionSuccess.treatmentReorder}</p> : null}
            </section>
        </div>
    );
}
