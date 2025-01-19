SELECT
    CASE
        WHEN oc.put_theta_decay_exp <= at.minimal_theta_decay THEN true
        ELSE false
        END AS passed,
    oc.put_theta_decay_exp as option,
    '<= '||at.minimal_theta_decay as threshold
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
