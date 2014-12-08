__author__ = 'Markus'
import os
import sqlite3
from collections import defaultdict

def convert(db_file, out_dir):
    if not out_dir:
        raise Exception("Must specify csv destination out_dir")
    if not os.path.isdir(out_dir):
        if os.path.exists(out_dir):
            raise Exception("File already exists at out_dir path.")
        else:
            os.makedirs(out_dir)

    def file_writer(probe):
        probe = probe.split('.')[-1]
        f = open(os.path.join(out_dir, probe) + ".json", 'w', encoding="utf-8-sig", newline='')
        #f.write(str('\ufeff'.encode('utf8'))) # BOM (optional...Excel needs it to open UTF-8 file properly)
        return f

    probe_to_files = {}


    _select_statement = "select * from data"
    conn = sqlite3.connect(db_file)
    cursor = conn.cursor()
    try:
        cursor.execute(_select_statement)
    except (sqlite3.OperationalError,sqlite3.DatabaseError):
        raise Exception("Unable to parse file: " + db_file)
    else:
        try:
            for row in cursor:
                _id, device, probe, timestamp, value = row
                file = probe_to_files.get(probe)
                if not file:
                    file = file_writer(probe)
                    probe_to_files[probe] = file
                file.write(value)
                file.write('\n')

        except IndexError:
            raise Exception("No file info exists in: " + db_file)