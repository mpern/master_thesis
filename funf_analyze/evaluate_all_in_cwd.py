#! python3.3
from viz_gen.data_models import SocialInteractionsModel, AudioDataModel, LocationsModel, BluetoothModel, CalendarModel

__author__ = 'Markus'

import os
import glob
import sqlite3
import csv
import json

from pytz import timezone
import pytz

from viz_gen.btid_label_cooccurence import  BluetoothLabelCooccurence
from viz_gen.social_heatmap import SocialHeatmapGenerator
from viz_gen.interaction_viz_gen import LocationVisualizations, DayTimeHeatMapGen, AudioVizGen, CalendarVizGen

from data_processing import dbmerge

from datetime import datetime, timedelta

from operator import itemgetter


import time
from contextlib import contextmanager

@contextmanager
def measureTime(title):
    t1 = time.perf_counter()
    yield
    t2 = time.perf_counter()
    ts = timedelta(seconds=(t2-t1))
    print('{}: {}'.format(title, str(ts)))

merged_file = None

interactions_csv = None

OUT_TZ = timezone('Europe/Vienna')

def convert_timestamp_to_local_timezone(ts):
    dt = datetime.utcfromtimestamp(ts)
    dt = dt.replace(tzinfo=pytz.utc)
    dt = dt.astimezone(OUT_TZ)
    return dt

INPUT_DATABASE_FOLDER = os.path.join(os.curdir, 'archive')

LABEL_PROBE = 'at.tugraz.knowcenter.funf.probes.LabelProbe'
SELECT_VALUES = 'select value from data where name = ?'

INTERACTIONS_FILE_NAME = 'interactions.csv'

INTERACTIONS_CSV_MAPPING = ('_id','activated_ts','deactivated_ts','label_id','label_text')

for file in os.listdir(os.curdir):
    if file.startswith('merged') and os.path.isfile(file):
        merged_file = file
    if file == INTERACTIONS_FILE_NAME and os.path.isfile(file):
        interactions_csv = file

if not interactions_csv:
    print('START generating {}'.format(INTERACTIONS_FILE_NAME))
    with measureTime('Extract Labels'):
        sorted_out = list()
        for file in glob.glob(os.path.join(INPUT_DATABASE_FOLDER, '*.db')):
            try:
                conn = sqlite3.connect(file)
                conn.row_factory = sqlite3.Row
                cursor = conn.cursor()

                cursor.execute(SELECT_VALUES, (LABEL_PROBE,))
                for row in cursor:
                    json_values = json.loads(row['value'])
                    sorted_out.append(json_values)

                cursor.close()
                conn.close()
            except (sqlite3.OperationalError,sqlite3.DatabaseError) as e:
                print('could not open db {}:'.format(file))
                print(e)

        if sorted_out:
            sorted_out = sorted(sorted_out, key=itemgetter('activated_ts'))
            with open(INTERACTIONS_FILE_NAME, mode='w', encoding='utf-8', newline='') as outfile:
                csvwriter = csv.DictWriter(outfile, INTERACTIONS_CSV_MAPPING, extrasaction='ignore', delimiter=';')
                csvwriter.writeheader()
                for value in sorted_out:
                    # format timestamps as strings for csv output
                    value['activated_ts'] = convert_timestamp_to_local_timezone(value['activated_ts']).isoformat()
                    value['deactivated_ts'] = convert_timestamp_to_local_timezone(value['deactivated_ts']).isoformat()
                    csvwriter.writerow(value)
            print("FINISHED generating {}".format(INTERACTIONS_FILE_NAME))

#DEBUG
#sys.exit()

#merge db
if not merged_file:
    with measureTime('Merge Database'):
        print("merging all *.db in archive/")
        merged = os.path.join(os.curdir, 'merged.db')
        merged = os.path.abspath(merged)
        cur_dir = os.path.abspath(os.curdir)
        os.chdir(INPUT_DATABASE_FOLDER)
        dbmerge.merge(out_file=merged)
        os.chdir(cur_dir)
        merged_file = merged

# #generate csv
# if not os.path.exists(os.path.join(os.curdir, 'csv')):
#     print('generating csv files from probes')
#     db2csv.convert(merged_file, 'csv')

out_folder = os.path.join(os.curdir, 'result')

os.makedirs(out_folder, exist_ok=True)

print("Evaluating {}".format(merged_file))


with measureTime('Building Data Models'):
    print("generating data models...")
    print("Interactions...")
    interactions = SocialInteractionsModel(merged_file)
    print("Interactions... DONE")
    print("Audio...")
    audio = AudioDataModel(merged_file)
    print("Audio... DONE")
    print("Locations...")
    locations = LocationsModel(merged_file)
    print("Locations... DONE")
    print("Bluetooth...")
    bluetooth = BluetoothModel(merged_file)
    print("Bluetooth... DONE")
    print("Calendar... ")
    try:
        calendar = CalendarModel(merged_file)
        print("Calendar... DONE")
    except AttributeError:
        print ('calendar not working')



print("generate location visualisation")
with measureTime('Location Vizualisation'):
    location_viz = LocationVisualizations(interactions, locations, out_folder)
    location_viz.generate_location_visualization()

print("generate heatmap visualisation")
with measureTime('Day-Time Heatmap'):
    heatmap_viz = DayTimeHeatMapGen(interactions, out_folder)
    heatmap_viz.generate_daytimeheatmap()

print("generate audio visualisation")
with measureTime('Audio Vizualisation'):
    audio_viz = AudioVizGen(interactions, audio, out_folder)
    audio_viz.generate_audio_visualization()

#heatmap.json
print("generate social heatmap")
with measureTime('Social Heatmap'):
    sh = SocialHeatmapGenerator(locations, interactions, out_folder)
    sh.generateHeatMapJson()

#co.json
print("generate bluetooth label")
with measureTime('Blueatooth-Label Cooccurence'):
    bt = BluetoothLabelCooccurence(interactions, bluetooth, out_folder)
#     bt.generateCooccurenceJson()

print("calendar")
with measureTime('Calendar'):
    cal = CalendarVizGen(interactions, calendar, out_folder)
    cal.generateCalendarVisualizations()
