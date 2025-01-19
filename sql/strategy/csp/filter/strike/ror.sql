SELECT
    CASE
        WHEN oc.put_ror >= 3.0 THEN true
        ELSE false
        END AS passed,
    oc.put_ror as option,
    '>= (static)'||3.0 as threshold
FROM
    optionchains oc
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
