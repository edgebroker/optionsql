WITH zgs AS (
    SELECT
        zgte.ticker_symbol,
        zgte.zero_gamma_level AS strike_price,
        COUNT(*) AS zgs_count
    FROM zero_gamma_ticker_expiration zgte
    GROUP BY zgte.ticker_symbol, zgte.zero_gamma_level
),
     tops AS (
         SELECT
             tse2.ticker_symbol,
             tse2.strike_price,
             COUNT(*) AS top_count
         FROM top_strikes_ticker_expiration tse2
         GROUP BY tse2.ticker_symbol, tse2.strike_price
     )
UPDATE top_strikes_ticker_expiration tse
SET
    zgs_count = COALESCE(zgs.zgs_count, 0), -- Use COALESCE to handle NULL values
    top_count = COALESCE(tops.top_count, 0)
FROM zgs
         FULL OUTER JOIN tops
                         ON zgs.ticker_symbol = tops.ticker_symbol
                             AND zgs.strike_price = tops.strike_price
WHERE tse.ticker_symbol = COALESCE(zgs.ticker_symbol, tops.ticker_symbol)
  AND tse.strike_price = COALESCE(zgs.strike_price, tops.strike_price);
