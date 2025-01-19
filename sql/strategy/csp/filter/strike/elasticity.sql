SELECT
    CASE
        WHEN oc.put_elasticity BETWEEN -0.25 AND at.balanced_elasticity THEN true
        ELSE false
        END AS passed
FROM
    optionchains oc
        JOIN
    adaptive_thresholds at
    ON oc.ticker_symbol = at.ticker_symbol
        AND oc.expiration_date = at.expiration_date
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
