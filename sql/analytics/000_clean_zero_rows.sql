WITH matched_rows AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price
    FROM
        optionchains
    WHERE
        iv = 0
       OR (bid = 0 AND ask = 0)
       OR (oi = 0 AND volume = 0)
       OR delta IS NULL
       OR iv IS NULL
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
