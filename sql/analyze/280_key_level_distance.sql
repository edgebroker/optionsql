-- Step 1: Calculate average key level distances for each ticker and expiration
WITH ranked_strikes AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,
        LEAD(strike_price) OVER (
            PARTITION BY ticker_symbol, expiration_date
            ORDER BY strike_price
            ) AS next_strike
    FROM
        top_strikes_ticker_expiration
),
     avg_key_levels AS (
         SELECT
             ticker_symbol,
             expiration_date,
             AVG(next_strike - strike_price) AS avg_key_level_distance
         FROM
             ranked_strikes
         WHERE
             next_strike IS NOT NULL
         GROUP BY
             ticker_symbol, expiration_date
     )

-- Step 2: Update ticker_expirations table with the computed averages
UPDATE ticker_expirations te
SET
    avg_key_level_distance = avg_data.avg_key_level_distance
FROM
    avg_key_levels avg_data
WHERE
    te.ticker_symbol = avg_data.ticker_symbol
  AND te.expiration_date = avg_data.expiration_date;
