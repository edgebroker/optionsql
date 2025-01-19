SELECT
    CASE
        WHEN oc.tte BETWEEN 30 AND 60 THEN true
        ELSE false
        END AS passed,
    oc.tte as option,
    '30 (static) to 60 (static)' as threshold
FROM
    optionchains oc
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
