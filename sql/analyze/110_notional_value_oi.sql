WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Notional Value of OI (sum of call and put open interest multiplied by strike price and 100)
        (strike_price * (COALESCE(call_oi, 0) + COALESCE(put_oi, 0)) * 100) AS notional_value_oi
    FROM
        optionchains
)
UPDATE optionchains AS o
SET notional_value_oi = om.notional_value_oi
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
