package com.reto.orders.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Resolución del correlation id")
class CorrelationIdFilterTest {

    @ParameterizedTest(name = "acepta ''{0}''")
    @ValueSource(strings = {"abc-123", "A", "0123456789012345678901234567890123456789012345678901234567890123"})
    void keepsSafeIds(String id) {
        assertThat(CorrelationIdFilter.resolveCorrelationId(id)).isEqualTo(id);
    }

    @ParameterizedTest(name = "rechaza ''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {
            "con espacio", "x\nlog falso", "<script>",
            // 65 caracteres: supera el máximo permitido
            "01234567890123456789012345678901234567890123456789012345678901234"})
    void replacesUnsafeIds(String id) {
        String resolved = CorrelationIdFilter.resolveCorrelationId(id);

        assertThat(resolved).isNotEqualTo(id);
        assertThat(UUID.fromString(resolved)).isNotNull();
    }

    @Test
    @DisplayName("Los ids generados son únicos")
    void generatedIdsAreUnique() {
        assertThat(CorrelationIdFilter.resolveCorrelationId(null))
                .isNotEqualTo(CorrelationIdFilter.resolveCorrelationId(null));
    }
}
