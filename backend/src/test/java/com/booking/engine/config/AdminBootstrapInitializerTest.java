package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.booking.engine.service.AdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private AdminBootstrapService adminBootstrapService;

    @InjectMocks
    private AdminBootstrapInitializer initializer;

    @Test
    void runDelegatesToBootstrapService() {
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(adminBootstrapService).bootstrapIfEnabled();
    }

    @Test
    void runPropagatesBootstrapFailuresToKeepStartupSafe() {
        doThrow(new IllegalStateException("bootstrap failed"))
                .when(adminBootstrapService).bootstrapIfEnabled();

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bootstrap failed");
    }
}
