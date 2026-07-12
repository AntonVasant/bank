-- Seed: 10 banks + reserves of 1 crore (10,000,000.000000) per currency (USD, INR)

-- ─── Banks ────────────────────────────────────────────────────────────────────
INSERT INTO banks (bank_code, country, name, description, contact_email, contact_phone, website, swift_code, status, created_at, updated_at)
VALUES
  ('HDFC_IN',   'IND', 'HDFC Bank',           'India''s largest private sector bank',                'ops@hdfc.in',     '+91-22-6652-1000', 'https://www.hdfcbank.com',   'HDFCINBB', 'ACTIVE', NOW(), NOW()),
  ('ICICI_IN',  'IND', 'ICICI Bank',           'Leading private bank in India',                       'ops@icici.in',    '+91-22-2653-1414', 'https://www.icicibank.com',  'ICICINBB', 'ACTIVE', NOW(), NOW()),
  ('SBI_IN',    'IND', 'State Bank of India',  'Largest public sector bank in India',                 'ops@sbi.in',      '+91-11-2374-0000', 'https://www.sbi.co.in',      'SBININBB', 'ACTIVE', NOW(), NOW()),
  ('AXIS_IN',   'IND', 'Axis Bank',            'Third largest private sector bank in India',          'ops@axisbank.in', '+91-22-2425-2525', 'https://www.axisbank.com',   'AXISINBB', 'ACTIVE', NOW(), NOW()),
  ('KOTAK_IN',  'IND', 'Kotak Mahindra Bank',  'Full-service commercial bank',                        'ops@kotak.in',    '+91-22-6600-6022', 'https://www.kotak.com',      'KKBKINBB', 'ACTIVE', NOW(), NOW()),
  ('YES_IN',    'IND', 'Yes Bank',             'Technology-first new-generation bank',                'ops@yesbank.in',  '+91-22-3347-7000', 'https://www.yesbank.in',     'YESBINBB', 'ACTIVE', NOW(), NOW()),
  ('PNB_IN',    'IND', 'Punjab National Bank', 'Second largest public sector bank',                   'ops@pnb.in',      '+91-11-2575-5500', 'https://www.pnbindia.in',    'PUNBINBB', 'ACTIVE', NOW(), NOW()),
  ('BOB_IN',    'IND', 'Bank of Baroda',       'Nationalised bank with global footprint',             'ops@bob.in',      '+91-265-2362-426', 'https://www.bankofbaroda.in','BARBINBB', 'ACTIVE', NOW(), NOW()),
  ('CANARA_IN', 'IND', 'Canara Bank',          'Major public sector bank headquartered in Bengaluru', 'ops@canara.in',   '+91-80-2222-1581', 'https://canarabank.com',     'CNRBINBB', 'ACTIVE', NOW(), NOW()),
  ('INDUS_IN',  'IND', 'IndusInd Bank',        'New-generation private sector bank',                  'ops@indusind.in', '+91-22-4406-6666', 'https://www.indusind.com',   'INDBINBB', 'ACTIVE', NOW(), NOW())
ON CONFLICT (bank_code) DO NOTHING;


