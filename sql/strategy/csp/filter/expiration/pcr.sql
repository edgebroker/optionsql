SELECT
    CASE
        WHEN ste.put_call_ratio BETWEEN at.min_pcr AND at.avg_pcr THEN true
        ELSE false
        END AS passed,
    ste.put_call_ratio as option,
    at.min_pcr||' to '||at.avg_bpr as threshold
FROM
    sentiment_ticker_expiration ste
        JOIN
    adaptive_thresholds at
    ON ste.ticker_symbol = at.ticker_symbol
        AND ste.expiration_date = at.expiration_date
WHERE
    ste.ticker_symbol = ?
  AND ste.expiration_date = ?;
