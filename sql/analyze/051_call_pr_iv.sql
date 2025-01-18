WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Percentile Rank of Call IV with secondary sorting for consistency
        PERCENT_RANK() OVER (
            PARTITION BY ticker_symbol, expiration_date
            ORDER BY call_iv, strike_price
            ) AS call_pr_iv
    FROM
        optionchains
)
UPDATE optionchains AS o
SET call_pr_iv = ROUND(om.call_pr_iv::NUMERIC, 6)
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
