package com.bredashboard.transactions.service;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionDashboardNumericParsingTest {

    @Test
    void decimal_acceptsStringValueFromSummaryView() throws Exception {
        ResultSet resultSet = resultSetReturning("87.27");

        assertThat(TransactionDashboardService.decimal(resultSet, "ECOM APPROVAL RATE"))
                .isEqualTo(87.27);
    }

    @Test
    void number_acceptsFormattedStringValueFromSummaryView() throws Exception {
        ResultSet resultSet = resultSetReturning("35,746");

        assertThat(TransactionDashboardService.number(resultSet, "TOTAL AUTH HITS"))
                .isEqualTo(35_746);
    }

    @Test
    void decimal_rejectsInvalidValueAsSqlException() throws Exception {
        ResultSet resultSet = resultSetReturning("not-a-rate");

        assertThatThrownBy(() -> TransactionDashboardService.decimal(resultSet, "ECOM APPROVAL RATE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ECOM APPROVAL RATE");
    }

    private ResultSet resultSetReturning(Object value) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(org.mockito.ArgumentMatchers.anyString())).thenReturn(value);
        return resultSet;
    }
}