function getEmployeeTreatmentIds(employee) {
    if (!Array.isArray(employee?.treatmentIds)) {
        return [];
    }

    return employee.treatmentIds.filter((value) => typeof value === 'string' && value.trim());
}

function doesEmployeeProvideTreatment(employee, treatmentId) {
    if (!employee || !treatmentId) {
        return false;
    }

    return getEmployeeTreatmentIds(employee).includes(treatmentId);
}

function getBookingEditBaseTreatmentOptions(sortedEmployees, sortedTreatments) {
    return sortedTreatments.filter((treatment) =>
        sortedEmployees.some((employee) => doesEmployeeProvideTreatment(employee, treatment.id))
    );
}

export function getBookingEditEmployeeOptions(sortedEmployees, treatmentId) {
    if (!treatmentId) {
        return sortedEmployees;
    }

    return sortedEmployees.filter((employee) =>
        doesEmployeeProvideTreatment(employee, treatmentId)
    );
}

export function getBookingEditTreatmentOptions(sortedEmployees, sortedTreatments, employeeId) {
    const selectedEmployee =
        sortedEmployees.find((employee) => employee.id === employeeId) || null;

    if (!selectedEmployee) {
        return getBookingEditBaseTreatmentOptions(sortedEmployees, sortedTreatments);
    }

    return sortedTreatments.filter((treatment) =>
        doesEmployeeProvideTreatment(selectedEmployee, treatment.id)
    );
}

export function normalizeBookingEditSelection(currentForm, sortedEmployees, sortedTreatments) {
    let nextEmployeeId = currentForm.employeeId || '';
    let nextTreatmentId = currentForm.treatmentId || '';

    if (nextEmployeeId && !sortedEmployees.some((employee) => employee.id === nextEmployeeId)) {
        nextEmployeeId = '';
    }

    if (nextTreatmentId) {
        const nextEmployees = getBookingEditEmployeeOptions(sortedEmployees, nextTreatmentId);

        if (nextEmployees.length === 0) {
            nextTreatmentId = '';
        } else if (!nextEmployees.some((employee) => employee.id === nextEmployeeId)) {
            nextEmployeeId = nextEmployees[0]?.id || '';
        }
    }

    const allowedTreatments = getBookingEditTreatmentOptions(
        sortedEmployees,
        sortedTreatments,
        nextEmployeeId
    );

    if (nextTreatmentId && !allowedTreatments.some((treatment) => treatment.id === nextTreatmentId)) {
        nextTreatmentId = allowedTreatments[0]?.id || '';
    }

    return {
        ...currentForm,
        employeeId: nextEmployeeId,
        treatmentId: nextTreatmentId
    };
}

export function getBookingEditFormForEmployeeChange(
    currentForm,
    nextEmployeeId,
    sortedEmployees,
    sortedTreatments
) {
    const nextTreatmentOptions = getBookingEditTreatmentOptions(
        sortedEmployees,
        sortedTreatments,
        nextEmployeeId
    );

    return {
        ...currentForm,
        employeeId: nextEmployeeId,
        treatmentId:
            currentForm.treatmentId
            && !nextTreatmentOptions.some((treatment) => treatment.id === currentForm.treatmentId)
                ? nextTreatmentOptions[0]?.id || ''
                : currentForm.treatmentId
    };
}

export function getBookingEditFormForTreatmentChange(currentForm, nextTreatmentId, sortedEmployees) {
    const nextEmployees = nextTreatmentId
        ? getBookingEditEmployeeOptions(sortedEmployees, nextTreatmentId)
        : sortedEmployees;

    return {
        ...currentForm,
        treatmentId: nextTreatmentId,
        employeeId: nextEmployees.some((employee) => employee.id === currentForm.employeeId)
            ? currentForm.employeeId
            : nextEmployees[0]?.id || ''
    };
}
