DO $$
DECLARE
    target_owner_id BIGINT;
    existing_owner_id BIGINT;
BEGIN
    SELECT id
    INTO target_owner_id
    FROM app_user
    WHERE email = 'REPLACE_WITH_ACCOUNT_EMAIL';

    IF target_owner_id IS NULL THEN
        RAISE EXCEPTION 'Account email was not found. Replace REPLACE_WITH_ACCOUNT_EMAIL before running this script.';
    END IF;

    SELECT owner_id
    INTO existing_owner_id
    FROM device
    WHERE device_id = '2496934';

    IF FOUND THEN
        IF existing_owner_id IS DISTINCT FROM target_owner_id THEN
            RAISE EXCEPTION 'Device ID 2496934 already belongs to a different account.';
        END IF;

        RAISE NOTICE 'Xiaozhi demo device 2496934 already exists for this account.';
        RETURN;
    END IF;

    INSERT INTO device (
        device_id,
        name,
        api_key_hash,
        owner_id,
        pin_hash,
        created_at
    )
    VALUES (
        '2496934',
        'Robot demo Xiaozhi',
        md5(random()::text || clock_timestamp()::text)
            || md5(random()::text || clock_timestamp()::text),
        target_owner_id,
        md5(random()::text || clock_timestamp()::text)
            || md5(random()::text || clock_timestamp()::text),
        now()
    );
END
$$;
