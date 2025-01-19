SELECT
    CASE
        WHEN ste.put_call_ratio BETWEEN 0.2 AND at.avg_pcr THEN true
        ELSE false
        END AS passed
FROM
    sentiment_ticker_expiration ste
        JOIN
    adaptive_thresholds at
    ON ste.ticker_symbol = at.ticker_symbol
        AND ste.expiration_date = at.expiration_date
WHERE
    ste.ticker_symbol = ?
  AND ste.expiration_date = ?;
