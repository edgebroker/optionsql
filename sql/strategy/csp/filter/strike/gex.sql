SELECT
    CASE
        WHEN oc.put_gex >= 0 THEN true
        ELSE false
        END AS passed
FROM
    optionchains oc
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
