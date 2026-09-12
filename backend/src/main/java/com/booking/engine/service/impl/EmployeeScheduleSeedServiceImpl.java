package com.booking.engine.service.impl;

import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.schedule.EmployeeScheduleGenerationDefaults;
import com.booking.engine.service.EmployeeScheduleSeedService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeScheduleSeedService}.
 * Provides employee schedule seed related business operations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeScheduleSeedServiceImpl implements EmployeeScheduleSeedService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(EmployeeScheduleSeedServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final EmployeeDailyScheduleRepository employeeDailyScheduleRepository;

    private final HairSalonRepository hairSalonRepository;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    private final HairSalonProperties hairSalonProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void seedUpcomingScheduleIfBookable(EmployeeEntity employee) {
        if (employee == null || employee.getId() == null || !Boolean.TRUE.equals(employee.getBookable())) {
            return;
        }

        LocalDate today = LocalDate.now(getZoneId());
        LocalDate endDate = today.plusDays(EmployeeScheduleGenerationDefaults.UPCOMING_DAYS_AHEAD - 1L);

        HairSalonEntity hairSalon = hairSalonRepository.findById(hairSalonProperties.getId()).orElse(null);
        if (hairSalon == null) {
            log.warn("event=employee_schedule_seed_skipped reason=hair_salon_not_found hairSalonId={}",
                    hairSalonProperties.getId());
            return;
        }

        Map<java.time.DayOfWeek, HairSalonHoursEntity> salonHoursByDay = new HashMap<>();
        hairSalon.getWorkingHours().forEach(hours -> salonHoursByDay.put(hours.getDayOfWeek(), hours));

        Map<LocalDate, EmployeeDailyScheduleEntity> existingByDate = employeeDailyScheduleRepository
                .findByEmployeeIdInAndWorkingDateBetween(List.of(employee.getId()), today, endDate)
                .stream()
                .collect(HashMap::new, (map, schedule) -> map.put(schedule.getWorkingDate(), schedule),
                        HashMap::putAll);

        List<EmployeeDailyScheduleEntity> toCreate = today.datesUntil(endDate.plusDays(1))
                .filter(date -> !existingByDate.containsKey(date))
                .map(date -> {
                    HairSalonHoursEntity defaultHours = salonHoursByDay.get(date.getDayOfWeek());
                    EmployeeDailyScheduleEntity schedule = new EmployeeDailyScheduleEntity();
                    schedule.setEmployee(employee);
                    schedule.setWorkingDate(date);

                    if (defaultHours == null || !defaultHours.isWorkingDay()) {
                        schedule.setWorkingDay(false);
                        schedule.setOpenTime(null);
                        schedule.setCloseTime(null);
                    } else {
                        schedule.setWorkingDay(true);
                        schedule.setOpenTime(defaultHours.getOpenTime());
                        schedule.setCloseTime(defaultHours.getCloseTime());
                    }

                    schedule.setBreakStartTime(null);
                    schedule.setBreakEndTime(null);
                    return schedule;
                })
                .toList();

        if (!toCreate.isEmpty()) {
            employeeDailyScheduleRepository.saveAll(toCreate);
            log.info("event=employee_schedule_seeded employeeId={} createdRows={}",
                    employee.getId(),
                    toCreate.size());
        }
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Resolves the configured booking timezone as a {@link ZoneId}.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }
}
