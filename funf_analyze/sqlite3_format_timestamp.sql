select id, device, probe, datetime(timestamp, 'unixepoch'), value from data where probe like '%Magnetic%'