package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.dto.AnalysisItemDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionAnalysisService {

    private final UserService userService;

    /**
     * Generates smart analysis based on transaction data for a specific user
     *
     * @param username The username of the user
     * @param startDate Optional start date for filtering transactions
     * @param endDate Optional end date for filtering transactions
     * @return List of analysis items with insights
     */
    public List<AnalysisItemDto> generateTransactionAnalysis(String username, LocalDate startDate, LocalDate endDate) {
        UserDto user = userService.getUserByUsernameWithTransactions(username);

        List<TransactionDto> allTransactions = new ArrayList<>();
        if (user.getBankConnections() != null) {
            user.getBankConnections().forEach(connection -> {
                if (connection.getAccounts() != null) {
                    connection.getAccounts().forEach(account -> {
                        if (account.getTransactions() != null) {
                            allTransactions.addAll(account.getTransactions());
                        }
                    });
                }
            });
        }

        List<TransactionDto> filteredTransactions = allTransactions;
        if (startDate != null || endDate != null) {
            filteredTransactions = allTransactions.stream()
                .filter(tx -> {
                    LocalDate txDate = tx.getValueDate() != null ? tx.getValueDate().toLocalDate() : null;
                    return txDate != null &&
                           (startDate == null || !txDate.isBefore(startDate)) &&
                           (endDate == null || !txDate.isAfter(endDate));
                })
                .collect(Collectors.toList());
        }

        if (filteredTransactions.isEmpty()) {
            log.info("No transactions found for analysis for user {}", username);
            return new ArrayList<>();
        }

        return performTransactionAnalysis(filteredTransactions);
    }

    /**
     * Performs the actual analysis on the transaction data
     *
     * @param transactions List of transactions to analyze
     * @return List of analysis items
     */
    private List<AnalysisItemDto> performTransactionAnalysis(List<TransactionDto> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            log.info("No transactions provided for analysis generation");
            return new ArrayList<>();
        }

        List<AnalysisItemDto> analysisItems = new ArrayList<>();
        String currency = transactions.getFirst().getCurrency();

        LocalDate now = LocalDate.now();
        LocalDate sevenDaysAgo = now.minusDays(7);
        LocalDate thirtyDaysAgo = now.minusDays(30);
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        LocalDate lastMonthStart = now.minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthEnd = currentMonthStart.minusDays(1);

        BigDecimal last7DaysExpense = BigDecimal.ZERO;
        BigDecimal last30DaysExpense = BigDecimal.ZERO;
        BigDecimal currentMonthExpense = BigDecimal.ZERO;
        BigDecimal lastMonthExpense = BigDecimal.ZERO;
        BigDecimal weekend30DaysExpense = BigDecimal.ZERO;
        BigDecimal weekday30DaysExpense = BigDecimal.ZERO;

        int weekendDaysCount = 0;
        int weekdayDaysCount = 0;
        for (int i = 0; i < 30; i++) {
            LocalDate date = now.minusDays(i);
            int dayOfWeek = date.getDayOfWeek().getValue();
            if (dayOfWeek >= 6) {
                weekendDaysCount++;
            } else {
                weekdayDaysCount++;
            }
        }

        Map<String, BigDecimal> currentMonthExpenseByCategory = new HashMap<>();
        Map<String, BigDecimal> lastMonthExpenseByCategory = new HashMap<>();

        for (TransactionDto tx : transactions) {
            if (tx.getAmount().compareTo(BigDecimal.ZERO) < 0) { // Only consider expenses (negative amounts)
                BigDecimal absAmount = tx.getAmount().abs();
                LocalDate txDate = tx.getValueDate().toLocalDate();

                if (isDateInRange(txDate, sevenDaysAgo, now)) {
                    last7DaysExpense = last7DaysExpense.add(absAmount);
                }

                if (isDateInRange(txDate, thirtyDaysAgo, now)) {
                    last30DaysExpense = last30DaysExpense.add(absAmount);
                    int dayOfWeek = txDate.getDayOfWeek().getValue();
                    if (dayOfWeek >= 6) { // 6 and 7 are weekend days
                        weekend30DaysExpense = weekend30DaysExpense.add(absAmount);
                    } else {
                        weekday30DaysExpense = weekday30DaysExpense.add(absAmount);
                    }
                }

                if (isDateInRange(txDate, currentMonthStart, now)) {
                    currentMonthExpense = currentMonthExpense.add(absAmount);
                    currentMonthExpenseByCategory.compute(tx.getCategory(), (k, v) ->
                        (v == null) ? absAmount : v.add(absAmount));
                }

                if (isDateInRange(txDate, lastMonthStart, lastMonthEnd)) {
                    lastMonthExpense = lastMonthExpense.add(absAmount);
                    lastMonthExpenseByCategory.compute(tx.getCategory(), (k, v) ->
                        (v == null) ? absAmount : v.add(absAmount));
                }
            }
        }

        BigDecimal weekdayAverage = weekdayDaysCount > 0 ?
            weekday30DaysExpense.divide(BigDecimal.valueOf(weekdayDaysCount), 2, java.math.RoundingMode.HALF_UP) :
            BigDecimal.ZERO;

        BigDecimal weekendAverage = weekendDaysCount > 0 ?
            weekend30DaysExpense.divide(BigDecimal.valueOf(weekendDaysCount), 2, java.math.RoundingMode.HALF_UP) :
            BigDecimal.ZERO;

        // Generate analysis items based on the data

        // 1. Last 7 days spending analysis
        if (last7DaysExpense.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyAverage = last7DaysExpense.divide(BigDecimal.valueOf(7), 2, java.math.RoundingMode.HALF_UP);
            String analysisText = String.format(
                "You've spent %.2f %s (avg %.2f/day). %s",
                last7DaysExpense,
                currency,
                dailyAverage,
                dailyAverage.compareTo(BigDecimal.valueOf(50)) > 0 ?
                    "Consider setting daily spending limits." :
                    "Keep maintaining this spending pattern."
            );

            analysisItems.add(AnalysisItemDto.builder()
                .period("LAST 7 DAYS")
                .text(analysisText)
                .icon("trending-up")
                .build());
        }

        // 2. Weekend vs Weekday spending patterns
        if (weekend30DaysExpense.compareTo(BigDecimal.ZERO) > 0 || weekday30DaysExpense.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal weekdayThreshold = weekdayAverage.multiply(BigDecimal.valueOf(1.5));
            BigDecimal weekendThreshold = weekendAverage.multiply(BigDecimal.valueOf(1.5));

            if (weekendAverage.compareTo(weekdayThreshold) > 0) {
                String analysisText = String.format(
                    "Your weekend spending (%.2f %s/day) is significantly higher than weekdays (%.2f %s/day). " +
                    "Try planning free or low-cost weekend activities.",
                    weekendAverage,
                    currency,
                    weekdayAverage,
                    currency
                );

                analysisItems.add(AnalysisItemDto.builder()
                    .period("WEEKEND HABITS")
                    .text(analysisText)
                    .icon("calendar")
                    .build());

            } else if (weekdayAverage.compareTo(weekendThreshold) > 0) {
                String analysisText = String.format(
                    "Your weekday spending (%.2f %s/day) is much higher than weekends (%.2f %s/day). " +
                    "Look for ways to reduce daily work expenses like bringing lunch from home.",
                    weekdayAverage,
                    currency,
                    weekendAverage,
                    currency
                );

                analysisItems.add(AnalysisItemDto.builder()
                    .period("WEEKDAY HABITS")
                    .text(analysisText)
                    .icon("briefcase")
                    .build());
            }
        }

        // 3. Top spending category analysis
        if (!currentMonthExpenseByCategory.isEmpty() && currentMonthExpense.compareTo(BigDecimal.ZERO) > 0) {
            Map.Entry<String, BigDecimal> topCategory = currentMonthExpenseByCategory.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

            if (topCategory != null) {
                String category = topCategory.getKey();
                BigDecimal amount = topCategory.getValue();
                BigDecimal percentage = amount.multiply(BigDecimal.valueOf(100))
                    .divide(currentMonthExpense, 0, java.math.RoundingMode.HALF_UP);

                if (percentage.compareTo(BigDecimal.valueOf(30)) > 0) {
                    String specificAdvice = isFoodRelatedCategory(category) ?
                        "Consider meal planning to reduce food costs." :
                        "Check if you can optimize this category.";

                    String analysisText = String.format(
                        "%d%% of your spending (%.2f %s) is on %s. %s",
                        percentage.intValue(),
                        amount,
                        currency,
                        category,
                        specificAdvice
                    );

                    analysisItems.add(AnalysisItemDto.builder()
                        .period("TOP CATEGORY")
                        .text(analysisText)
                        .icon("pie-chart")
                        .build());
                }
            }
        }

        // 4. Month-over-month spending trend
        if (currentMonthExpense.compareTo(BigDecimal.ZERO) > 0 && lastMonthExpense.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal difference = currentMonthExpense.subtract(lastMonthExpense);
            BigDecimal percentageChange = difference.multiply(BigDecimal.valueOf(100))
                .divide(lastMonthExpense, 1, java.math.RoundingMode.HALF_UP);

            String trend = percentageChange.compareTo(BigDecimal.ZERO) > 0 ? "increased" : "decreased";
            String sentiment = percentageChange.compareTo(BigDecimal.ZERO) > 0 ? "Consider reviewing your budget." : "Great job controlling your spending!";

            // Only show significant changes (more than 5%)
            if (percentageChange.abs().compareTo(new BigDecimal("5")) > 0) {
                String analysisText = String.format(
                    "Your spending has %s by %.1f%% compared to last month. %s",
                    trend,
                    percentageChange.abs(),
                    sentiment
                );

                analysisItems.add(AnalysisItemDto.builder()
                    .period("MONTHLY TREND")
                    .text(analysisText)
                    .icon(percentageChange.compareTo(BigDecimal.ZERO) > 0 ? "trending-up" : "trending-down")
                    .build());
            }
        }

        // 5. Recurring subscription detection
        Map<String, List<TransactionDto>> potentialSubscriptions = findRecurringTransactions(transactions);
        if (!potentialSubscriptions.isEmpty()) {
            int totalSubscriptionAmount = potentialSubscriptions.values().stream()
                .mapToInt(list -> list.getFirst().getAmount().abs().intValue())
                .sum();

            String merchantsList = potentialSubscriptions.keySet().stream()
                .limit(3)
                .collect(Collectors.joining(", "));

            if (potentialSubscriptions.size() > 3) {
                merchantsList += " and others";
            }

            String analysisText = String.format(
                "You have approximately %d recurring payments including %s, totaling around %d %s per month. Review these subscriptions regularly.",
                potentialSubscriptions.size(),
                merchantsList,
                totalSubscriptionAmount,
                currency
            );

            analysisItems.add(AnalysisItemDto.builder()
                .period("SUBSCRIPTIONS")
                .text(analysisText)
                .icon("repeat")
                .build());
        }

        // 6. Unusually large transactions
        List<TransactionDto> largeTransactions = findUnusuallyLargeTransactions(transactions);
        if (!largeTransactions.isEmpty()) {
            BigDecimal largestAmount = largeTransactions.getFirst().getAmount().abs();
            String merchant = extractMerchantName(largeTransactions.getFirst().getDescription());

            String analysisText = String.format(
                "There was an unusually large transaction of %.2f %s to %s. Make sure this was an authorized payment.",
                largestAmount,
                currency,
                merchant
            );

            analysisItems.add(AnalysisItemDto.builder()
                .period("UNUSUAL ACTIVITY")
                .text(analysisText)
                .icon("alert-circle")
                .build());
        }

        return analysisItems;
    }

    /**
     * Identifies transactions that appear to be recurring subscriptions
     *
     * @param transactions List of transactions to analyze
     * @return Map of merchant names to their recurring transactions
     */
    private Map<String, List<TransactionDto>> findRecurringTransactions(List<TransactionDto> transactions) {
        Map<String, List<TransactionDto>> merchantTransactions = new HashMap<>();

        // Group transactions by description/merchant
        for (TransactionDto tx : transactions) {
            if (tx.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                String merchantName = extractMerchantName(tx.getDescription());
                if (!merchantName.isEmpty()) {
                    merchantTransactions.computeIfAbsent(merchantName, k -> new ArrayList<>()).add(tx);
                }
            }
        }

        // Filter to find merchants with consistent payment amounts
        return merchantTransactions.entrySet().stream()
            .filter(entry -> {
                List<TransactionDto> txList = entry.getValue();
                // Need at least 2 transactions to consider as recurring
                if (txList.size() < 2) return false;

                // Check if all transactions have same or very similar amounts
                BigDecimal firstAmount = txList.getFirst().getAmount().abs();
                return txList.stream().allMatch(tx ->
                    tx.getAmount().abs().subtract(firstAmount).abs().compareTo(BigDecimal.ONE) <= 0
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Identifies transactions that are unusually large compared to the user's average spending
     *
     * @param transactions List of transactions to analyze
     * @return List of unusually large transactions
     */
    private List<TransactionDto> findUnusuallyLargeTransactions(List<TransactionDto> transactions) {
        if (transactions.isEmpty()) return List.of();

        BigDecimal totalAmount = transactions.stream()
            .filter(tx -> tx.getAmount().compareTo(BigDecimal.ZERO) < 0) // Only expenses
            .map(tx -> tx.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal count = BigDecimal.valueOf(transactions.stream()
            .filter(tx -> tx.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .count());

        if (count.compareTo(BigDecimal.ZERO) == 0) return List.of();

        BigDecimal average = totalAmount.divide(count, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal threshold = average.multiply(new BigDecimal("3")); // 3x the average

        // Find transactions larger than threshold
        return transactions.stream()
            .filter(tx -> tx.getAmount().compareTo(BigDecimal.ZERO) < 0) // Only expenses
            .filter(tx -> tx.getAmount().abs().compareTo(threshold) > 0)
            .sorted((tx1, tx2) -> tx2.getAmount().abs().compareTo(tx1.getAmount().abs())) // Largest first
            .limit(3) // Only the top 3
            .collect(Collectors.toList());
    }

    /**
     * Extracts a merchant name from transaction description
     *
     * @param description The transaction description
     * @return A simplified merchant name
     */
    private String extractMerchantName(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }

        String[] parts = description.split("\\s+");
        if (parts.length == 0) return "";

        String merchantName = parts[0];
        if (merchantName.length() < 3 && parts.length > 1) {
            merchantName = parts[1];
        }

        return merchantName.replaceAll("[^a-zA-Z0-9]", "");
    }

    private boolean isFoodRelatedCategory(String category) {
        String lowerCategory = category.toLowerCase();
        return lowerCategory.contains("food") ||
               lowerCategory.contains("dining") ||
               lowerCategory.contains("restaurant") ||
               lowerCategory.contains("groceries");
    }

    private boolean isDateInRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
