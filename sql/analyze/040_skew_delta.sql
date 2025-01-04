WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Call/Put Delta Skew
        call_delta - put_delta AS skew_delta

    FROM
        optionchains
)
UPDATE optionchains AS o
SET skew_delta = om.skew_delta
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
