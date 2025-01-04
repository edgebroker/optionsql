WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Time to Expiration (TTE) in years
        (expiration_date::DATE - CURRENT_DATE)::NUMERIC / 365 AS time_to_expiration
    FROM
        optionchains
)
UPDATE optionchains AS o
SET time_to_expiration = om.time_to_expiration
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
