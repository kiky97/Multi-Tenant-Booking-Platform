package com.booking.engine.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.service.HairSalonHoursService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HairSalonHoursAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HairSalonHoursService hoursService;

    private UUID salonId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        salonId = UUID.randomUUID();
        HairSalonHoursAdminController controller = new HairSalonHoursAdminController(hoursService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllHoursReturnsList() throws Exception {
        HairSalonHoursResponseDto dto = HairSalonHoursResponseDto.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(17, 0))
                .build();
        when(hoursService.getWorkingHours(salonId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/hair-salons/{id}/hours", salonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dayOfWeek", is("MONDAY")))
                .andExpect(jsonPath("$[0].workingDay", is(true)));
    }

    @Test
    void updateHoursReturns204() throws Exception {
        mockMvc.perform(put("/api/v1/admin/hair-salons/{id}/hours/{day}", salonId, "TUESDAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workingDay\":false}"))
                .andExpect(status().isNoContent());

        verify(hoursService).updateWorkingDay(eq(salonId), eq(DayOfWeek.TUESDAY), any(HairSalonHoursRequestDto.class));
    }
}
