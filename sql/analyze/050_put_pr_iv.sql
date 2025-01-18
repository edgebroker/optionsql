WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Corrected Percentile Rank of Put IV
        PERCENT_RANK() OVER (
            PARTITION BY ticker_symbol, expiration_date
            ORDER BY put_iv, strike_price
            ) AS put_pr_iv
    FROM
        optionchains
)
UPDATE optionchains AS o
SET put_pr_iv = ROUND(om.put_pr_iv::NUMERIC, 6)  -- Round to 6 decimal places
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
