-- Insert data into zero_gamma_ticker_expiration
INSERT INTO zero_gamma_ticker_expiration (ticker_symbol, expiration_date, zero_gamma_level, flip_gamma)
WITH gex_data AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,
        -- Compute cumulative GEX
        SUM(call_gex - put_gex) OVER (
            PARTITION BY ticker_symbol, expiration_date
            ORDER BY strike_price
            ) AS cumulative_gex
    FROM optionchains
),
     lagged_gex_data AS (
         SELECT
             ticker_symbol,
             expiration_date,
             strike_price AS zero_gamma_level,
             cumulative_gex,
             LAG(cumulative_gex) OVER (
                 PARTITION BY ticker_symbol, expiration_date
                 ORDER BY strike_price
                 ) AS prev_cumulative_gex
         FROM gex_data
     ),
     gamma_flips AS (
         SELECT
             ticker_symbol,
             expiration_date,
             zero_gamma_level,
             -- Flip gamma is the absolute sum of GEX changes around the zero-crossing
             ABS(prev_cumulative_gex) + ABS(cumulative_gex) AS flip_gamma
         FROM lagged_gex_data
         WHERE cumulative_gex * prev_cumulative_gex < 0 -- Detect sign change
     )
SELECT
    ticker_symbol,
    expiration_date,
    zero_gamma_level,
    flip_gamma
FROM gamma_flips;
