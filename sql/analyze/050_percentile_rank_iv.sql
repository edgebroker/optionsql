WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Calculate Percentile Rank of IV using the maximum of call_iv and put_iv
        PERCENT_RANK() OVER (
            PARTITION BY ticker_symbol, expiration_date
            ORDER BY GREATEST(call_iv, put_iv)
            ) AS percentile_rank_iv
    FROM
        optionchains
)
UPDATE optionchains AS o
SET percentile_rank_iv = ROUND(om.percentile_rank_iv::NUMERIC, 6)  -- Cast to NUMERIC and round to 6 decimal places
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
