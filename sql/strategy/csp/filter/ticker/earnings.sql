SELECT DISTINCT
    CASE
        WHEN t.next_earnings_date IS NOT NULL
            AND t.next_earnings_date != 'N/A'
            AND TO_DATE(t.next_earnings_date, 'YYYY-MM-DD') <= CURRENT_DATE + INTERVAL '7 days' THEN FALSE
        ELSE TRUE
        END AS passed
FROM ticker t
WHERE t.ticker_symbol = ?;
