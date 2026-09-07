package com.yigitcicekci.tillora.dashboard.infrastructure.persistence;

import com.yigitcicekci.tillora.dashboard.api.response.DashboardAlerts;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardCards;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardQueryRepository {

    private static final String SUMMARY_QUERY = """
        WITH voucher_account_balances AS (
            SELECT
                line.current_account_id,
                sum((line.debit - line.credit) * voucher.exchange_rate) AS balance,
                sum(CASE
                    WHEN line.due_date < :today
                    THEN line.debit * voucher.exchange_rate
                    ELSE 0
                END) AS overdue_debits,
                sum(line.credit * voucher.exchange_rate) AS total_credits,
                sum(CASE
                    WHEN line.due_date BETWEEN :today AND :upcoming_end
                    THEN line.credit * voucher.exchange_rate
                    ELSE 0
                END) AS upcoming_credits
            FROM voucher_lines line
            JOIN vouchers voucher
              ON voucher.id = line.voucher_id
             AND voucher.company_id = line.company_id
            WHERE line.company_id = :company_id
              AND voucher.status = 'APPROVED'
              AND line.current_account_id IS NOT NULL
            GROUP BY line.current_account_id
        ),
        opening_account_balances AS (
            SELECT
                movement.current_account_id,
                sum(movement.debit - movement.credit) AS balance
            FROM current_account_movements movement
            WHERE movement.company_id = :company_id
              AND movement.movement_type = 'OPENING_BALANCE'
            GROUP BY movement.current_account_id
        ),
        account_balances AS (
            SELECT
                account.id AS current_account_id,
                coalesce(voucher.balance, 0) + coalesce(opening.balance, 0) AS balance,
                coalesce(voucher.overdue_debits, 0) AS overdue_debits,
                coalesce(voucher.total_credits, 0) AS total_credits,
                coalesce(voucher.upcoming_credits, 0) AS upcoming_credits
            FROM current_accounts account
            LEFT JOIN voucher_account_balances voucher
              ON voucher.current_account_id = account.id
            LEFT JOIN opening_account_balances opening
              ON opening.current_account_id = account.id
            WHERE account.company_id = :company_id
        ),
        account_summary AS (
            SELECT
                coalesce(sum(greatest(balance, 0)), 0) AS total_receivables,
                coalesce(sum(greatest(-balance, 0)), 0) AS total_payables,
                coalesce(sum(greatest(overdue_debits - total_credits, 0)), 0)
                    AS overdue_receivables,
                count(*) FILTER (
                    WHERE greatest(overdue_debits - total_credits, 0) > 0
                ) AS overdue_collections,
                count(*) FILTER (
                    WHERE balance < 0 AND upcoming_credits > 0
                ) AS upcoming_payments
            FROM account_balances
        ),
        settlement_summary AS (
            SELECT
                coalesce(sum(CASE
                    WHEN cash.id IS NOT NULL
                    THEN (line.debit - line.credit) * voucher.exchange_rate
                    ELSE 0
                END), 0) AS cash_balance,
                coalesce(sum(CASE
                    WHEN bank.id IS NOT NULL
                    THEN (line.debit - line.credit) * voucher.exchange_rate
                    ELSE 0
                END), 0) AS bank_balance
            FROM voucher_lines line
            JOIN vouchers voucher
              ON voucher.id = line.voucher_id
             AND voucher.company_id = line.company_id
            LEFT JOIN cash_accounts cash
              ON cash.company_id = line.company_id
             AND cash.chart_of_account_id = line.chart_of_account_id
            LEFT JOIN bank_accounts bank
              ON bank.company_id = line.company_id
             AND bank.chart_of_account_id = line.chart_of_account_id
            WHERE line.company_id = :company_id
              AND voucher.status = 'APPROVED'
        ),
        invoice_summary AS (
            SELECT
                coalesce(sum(grand_total * exchange_rate) FILTER (
                    WHERE invoice_type = 'SALES'
                ), 0) AS monthly_sales,
                coalesce(sum(grand_total * exchange_rate) FILTER (
                    WHERE invoice_type = 'PURCHASE'
                ), 0) AS monthly_purchases
            FROM invoices
            WHERE company_id = :company_id
              AND status = 'APPROVED'
              AND invoice_date >= :period_start
              AND invoice_date < :period_end
        ),
        voucher_summary AS (
            SELECT
                coalesce(sum(total_debit * exchange_rate) FILTER (
                    WHERE voucher_type = 'COLLECTION'
                ), 0) AS collections,
                coalesce(sum(total_debit * exchange_rate) FILTER (
                    WHERE voucher_type = 'PAYMENT'
                ), 0) AS payments
            FROM vouchers
            WHERE company_id = :company_id
              AND status = 'APPROVED'
              AND voucher_date >= :period_start
              AND voucher_date < :period_end
        )
        SELECT
            account.total_receivables,
            account.overdue_receivables,
            account.total_payables,
            settlement.cash_balance,
            settlement.bank_balance,
            invoice.monthly_sales,
            invoice.monthly_purchases,
            voucher.collections,
            voucher.payments,
            account.overdue_collections,
            account.upcoming_payments
        FROM account_summary account
        CROSS JOIN settlement_summary settlement
        CROSS JOIN invoice_summary invoice
        CROSS JOIN voucher_summary voucher
        """;

    private final JdbcClient jdbcClient;

    public DashboardQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DashboardSummary summary(
        UUID companyId,
        YearMonth period,
        String currency,
        LocalDate today
    ) {
        return jdbcClient.sql(SUMMARY_QUERY)
            .param("company_id", companyId)
            .param("today", today)
            .param("upcoming_end", today.plusDays(7))
            .param("period_start", period.atDay(1))
            .param("period_end", period.plusMonths(1).atDay(1))
            .query((result, rowNumber) -> new DashboardSummary(
                period,
                currency,
                new DashboardCards(
                    amount(result.getBigDecimal("total_receivables")),
                    amount(result.getBigDecimal("overdue_receivables")),
                    amount(result.getBigDecimal("total_payables")),
                    amount(result.getBigDecimal("cash_balance")),
                    amount(result.getBigDecimal("bank_balance")),
                    amount(result.getBigDecimal("monthly_sales")),
                    amount(result.getBigDecimal("monthly_purchases")),
                    amount(result.getBigDecimal("collections")),
                    amount(result.getBigDecimal("payments"))
                ),
                new DashboardAlerts(
                    result.getLong("overdue_collections"),
                    result.getLong("upcoming_payments")
                ),
                Instant.now()
            ))
            .single();
    }

    private BigDecimal amount(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
