SELECT
    CASE
        WHEN oc.tte BETWEEN 30 AND 60 THEN true
        ELSE false
        END AS passed
FROM
    optionchains oc
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
