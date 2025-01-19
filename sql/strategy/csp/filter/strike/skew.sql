SELECT
    CASE
        WHEN oc.skew <= at.controlled_skew THEN true
        ELSE false
        END AS passed,
    oc.skew as option,
    '<= '||at.controlled_skew as threshold
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
