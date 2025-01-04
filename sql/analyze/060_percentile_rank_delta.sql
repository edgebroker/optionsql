WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,
        -- Percentile Rank of Delta (using the greater absolute value of call and put deltas)
        PERCENT_RANK() OVER (
            PARTITION BY ticker_symbol, expiration_date
            ORDER BY GREATEST(ABS(call_delta), ABS(put_delta))
            ) AS percentile_rank_delta
    FROM
        optionchains
)
UPDATE optionchains AS o
SET percentile_rank_delta = om.percentile_rank_delta
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
