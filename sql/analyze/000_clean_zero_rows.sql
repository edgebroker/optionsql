WITH matched_rows AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price
    FROM
        optionchains
    WHERE
       -- Check for zero implied volatility for both calls and puts
        (call_iv = 0 OR put_iv = 0)
       OR
       -- Check for zero bid and ask for both calls and puts
        ((call_bid = 0 AND call_ask = 0) OR (put_bid = 0 AND put_ask = 0))
       OR
       -- Check for zero open interest and volume for both calls and puts
        ((call_oi = 0 AND call_volume = 0) OR (put_oi = 0 AND put_volume = 0))
       OR
       -- Check for null delta or implied volatility for both calls and puts
        (call_delta IS NULL OR put_delta IS NULL OR call_iv IS NULL OR put_iv IS NULL)
    GROUP BY
        ticker_symbol,
        expiration_date,
        strike_price
)
DELETE FROM optionchains
WHERE (ticker_symbol, expiration_date, strike_price) IN (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price
    FROM
        matched_rows
);
