package com.booking.engine.service;

import com.booking.engine.entity.EmployeeEntity;

/**
 * Service contract for employee schedule seed operations.
 * Defines employee schedule seed related business operations.
 */
public interface EmployeeScheduleSeedService {

    /**
     * Seeds upcoming schedule rows for a bookable employee when required.
     *
     * @param employee employee entity
     */
    void seedUpcomingScheduleIfBookable(EmployeeEntity employee);
}
