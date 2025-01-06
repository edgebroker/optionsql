-- Step 1: Compute expected moves for all tickers and ensure deduplication
WITH closest_strikes AS (
    SELECT
        o.ticker_symbol,
        o.expiration_date,
        o.strike_price,
        o.call_iv,
        o.put_iv,
        ABS(o.strike_price - t.current_price) AS distance_to_current_price,
        t.current_price
    FROM
        optionchains o
            JOIN
        ticker t ON o.ticker_symbol = t.ticker_symbol
),
     closest_iv AS (
         SELECT
             ticker_symbol,
             expiration_date,
             current_price,
             -- Get the closest strike price's call and put IV
             (call_iv + put_iv) / 2 AS implied_volatility
         FROM (
                  SELECT
                      ticker_symbol,
                      expiration_date,
                      current_price,
                      call_iv,
                      put_iv,
                      RANK() OVER (PARTITION BY ticker_symbol, expiration_date ORDER BY distance_to_current_price ASC) AS rank
                  FROM
                      closest_strikes
              ) sub
         WHERE rank = 1
     ),
     expected_moves AS (
         SELECT
             ci.ticker_symbol,
             ci.expiration_date,
             ci.implied_volatility,
             ci.current_price,
             -- Compute days to expiration
             CAST(TO_DATE(ci.expiration_date, 'YYYY-MM-DD') - CURRENT_DATE AS INTEGER) AS days_to_expiration,
             -- Compute expected move in dollars
             ci.current_price * ci.implied_volatility * SQRT(CAST(TO_DATE(ci.expiration_date, 'YYYY-MM-DD') - CURRENT_DATE AS DOUBLE PRECISION) / 365.0) AS expected_move_dollars
         FROM
             closest_iv ci
         WHERE
             CAST(TO_DATE(ci.expiration_date, 'YYYY-MM-DD') - CURRENT_DATE AS INTEGER) > 0
     ),
     final_expected_moves AS (
         SELECT DISTINCT
             em.ticker_symbol,
             em.expiration_date,
             MAX(em.expected_move_dollars) AS expected_move_dollars,
             MAX((em.expected_move_dollars / em.current_price) * 100) AS expected_move_percent,
             MAX(em.implied_volatility) AS implied_volatility,
             MAX(em.days_to_expiration) AS days_to_expiration
         FROM
             expected_moves em
         GROUP BY em.ticker_symbol, em.expiration_date
     )
-- Insert the computed expected moves into ticker_expirations
INSERT INTO ticker_expirations (ticker_symbol, expiration_date, expected_move_dollars, expected_move_percent, implied_volatility, days_to_expiration)
SELECT
    ticker_symbol,
    expiration_date,
    expected_move_dollars,
    expected_move_percent,
    implied_volatility,
    days_to_expiration
FROM
    final_expected_moves
ON CONFLICT (ticker_symbol, expiration_date)
    DO UPDATE
    SET
        expected_move_dollars = EXCLUDED.expected_move_dollars,
        expected_move_percent = EXCLUDED.expected_move_percent,
        implied_volatility = EXCLUDED.implied_volatility,
        days_to_expiration = EXCLUDED.days_to_expiration;
