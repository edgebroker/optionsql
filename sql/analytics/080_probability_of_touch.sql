WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,
        side,
        delta,

        -- Correct Probability of Touch (POT)
        CASE
            WHEN side = 'Call' THEN GREATEST(0, LEAST(1, 2 * delta))
            WHEN side = 'Put' THEN GREATEST(0, LEAST(1, 2 * ABS(delta)))
            ELSE NULL
            END AS probability_of_touch

    FROM
        optionchains
)
UPDATE optionchains AS o
SET probability_of_touch = om.probability_of_touch
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price
  AND o.side = om.side;
