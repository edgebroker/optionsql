WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Expected Move Percentage (using precomputed implied move)
        implied_move / NULLIF(((call_bid + call_ask + put_bid + put_ask) / 4), 0) * 100 AS expected_move_percentage
    FROM
        optionchains
)
UPDATE optionchains AS o
SET expected_move_percentage = om.expected_move_percentage
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
