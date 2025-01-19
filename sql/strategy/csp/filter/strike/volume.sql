SELECT
    CASE
        WHEN oc.put_volume >= 100 THEN true
        ELSE false
        END AS passed
FROM
    optionchains oc
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
