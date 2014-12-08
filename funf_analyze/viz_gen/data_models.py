import sqlite3
import json
from collections import defaultdict
from itertools import groupby
from operator import itemgetter
import abc
import os

import pandas as pd


class DataBaseHelper:
    def __init__(self, db_file):
        self.dbfile = db_file
        #check for errors
        conn = sqlite3.connect(db_file)
        conn.close()

    def getJsonDataForProbes(self, probe_keys):
        conn = sqlite3.connect(self.dbfile)
        try:
            c = conn.cursor()
            tuple_string = ",".join(("?" for i in range(len(probe_keys))))
            c.execute('select * from data where probe in ({}) ORDER BY timestamp'.format(tuple_string), probe_keys)
            json_data = list()
            for row in c:
                data = row[4]
                data_dict = json.loads(data.rstrip())
                json_data.append(data_dict)
            return json_data
        finally:
            conn.close()


class ModelBase(metaclass=abc.ABCMeta):
    FUNF_TO_PD_TIMESCALE = 1e9



    def __init__(self, db_name):
        self.columns=["timestamp"]
        self.db_helper = DataBaseHelper(db_name)
        self.json_data = list()
        self.data_frame = pd.DataFrame(columns=self.columns)

    @classmethod
    def _prepareDataFrame(cls, data_frame):
        data_frame.timestamp = pd.to_datetime(data_frame.timestamp * cls.FUNF_TO_PD_TIMESCALE)
        data_frame.index = data_frame.timestamp
        return data_frame.sort()



    @classmethod
    def generateDataFrameFromDict(cls, input):
        data_frame = pd.DataFrame.from_dict(input)
        return cls._prepareDataFrame(data_frame)

    @classmethod
    def generateDataFrameFromJson(cls, json_in):
        data_frame = pd.read_json(json.dumps(json_in))
        return cls._prepareDataFrame(data_frame)


class SocialInteractionsModel(ModelBase):
    CORRECTED_FILE = './interactions_corrected.csv'
    def __init__(self, db_name):
        super().__init__(db_name)
        self.interaction_progress = None
        self._load_label_data()

    def _load_label_data(self):

        if(os.path.isfile(self.CORRECTED_FILE)):
            self.data_frame = pd.read_csv(self.CORRECTED_FILE, delimiter=';', parse_dates=[1,2])
            self.data_frame.index = self.data_frame.activated_ts
        else:
            probes = ('at.tugraz.knowcenter.funf.probes.LabelProbe', )

            self.json_data = self.db_helper.getJsonDataForProbes(probes)

            label_data = pd.read_json(json.dumps(self.json_data))
            label_data.activated_ts = pd.to_datetime(label_data.activated_ts * self.FUNF_TO_PD_TIMESCALE)
            label_data.deactivated_ts = pd.to_datetime(label_data.deactivated_ts * self.FUNF_TO_PD_TIMESCALE)
            label_data.index = label_data.activated_ts

            self.data_frame = label_data
        self.data_frame = self.data_frame.sort()

        progress = list()
        for _, row in self.data_frame.iterrows():
            progress += [(row['activated_ts'], 1)]
            progress += [(row['deactivated_ts'], -1)]

        progress = sorted(progress, key=itemgetter(0))
        #group by equal timestamps (funf: max precision = 1 sec)
        groups = groupby(progress, itemgetter(0))
        # and sum up activate / deactivate actions
        progress = [(key, sum(d[1] for d in data)) for key, data in groups]

        pandas_in = defaultdict(list)
        num_of_active_interactions = 0

        for step in progress:
            num_of_active_interactions += step[1]
            pandas_in['ts'].append(step[0])
            pandas_in['num_interactions'].append(num_of_active_interactions)

        self.interaction_progress = pd.DataFrame(pandas_in)
        self.interaction_progress.index = self.interaction_progress.ts

    #find interaction in bin:
    #   interaction ending in current timeframe
    #   interaction starting in current timeframe
    #   interaction active during current timefraem
    #HINT first two restrictions also get all interaction inside current timeframe
    def count_interactions_between(self, start, end):
        selector = ((self.data_frame.deactivated_ts <= end) & (self.data_frame.deactivated_ts >= start)) | \
                   ((self.data_frame.activated_ts >= start) & (self.data_frame.activated_ts <= end)) | \
                   ((self.data_frame.activated_ts <= start ) & (self.data_frame.deactivated_ts >= end))
        num_interactions = self.data_frame[selector].count()['_id']
        return num_interactions

    def get_labels_between(self, start, end):
        selector = ((self.data_frame.deactivated_ts <= end) & (self.data_frame.deactivated_ts >= start)) | \
                   ((self.data_frame.activated_ts >= start) & (self.data_frame.activated_ts <= end)) | \
                   ((self.data_frame.activated_ts <= start ) & (self.data_frame.deactivated_ts >= end))
        label_list = self.data_frame[selector]['label_text'].values.tolist()
        return_set = set(label_list)  #avoid duplicates
        return return_set

    def get_min_timestamp(self):
        return self.data_frame.activated_ts.min()

    def get_max_timestamp(self):
        return self.data_frame.deactivated_ts.max()


class LocationsModel(ModelBase):
    def __init__(self, db_name):
        super().__init__(db_name)
        self._load_locations_data()

    def _load_locations_data(self):
        probes = ('edu.mit.media.funf.probe.builtin.SimpleLocationProbe',)
        self.json_data = self.db_helper.getJsonDataForProbes(probes)

        self.json_data = sorted(self.json_data, key=itemgetter('mTime'))


class BluetoothModel(ModelBase):
    def __init__(self, db_name):
        super().__init__(db_name)
        self._load_bt_data()

    def _load_bt_data(self):
        probes = ('edu.mit.media.funf.probe.builtin.BluetoothProbe',)
        self.json_data = self.db_helper.getJsonDataForProbes(probes)


class CalendarModel(ModelBase):
    ATTENDEE_LIST_KEY = 'ATTENDEES'

    def __init__(self, db_name):
        self.columns = ['timestamp',
                        'begin',
                        'end',
                        'num_attendees',
                        CalendarModel.ATTENDEE_LIST_KEY,
                        'event_id',
                        'hasAttendeeData',
                        'title']
        super().__init__(db_name)
        self._load_calendar()

    # http://developer.android.com/reference/android/provider/CalendarContract.Instances.html
    # http://developer.android.com/reference/android/provider/CalendarContract.AttendeesColumns.html#EVENT_ID
    def _load_calendar(self):
        probes = ('at.tugraz.knowcenter.funf.probes.CalendarProbe',)
        json_in = self.db_helper.getJsonDataForProbes(probes)
        id = itemgetter('_id')
        json_in = sorted(json_in, key=id)

        pandas_in = list()
        key_for_duplicate_check = itemgetter('begin')
        for key, data in groupby(json_in, id):
            old_key = None
            for instance in sorted(data, key=key_for_duplicate_check):
                if not old_key or old_key != key_for_duplicate_check(instance):
                    instance['num_attendees'] = len(instance[CalendarModel.ATTENDEE_LIST_KEY])
                    pandas_in.append(instance)
                old_key = key_for_duplicate_check(instance)

        if pandas_in:
            self.json_data = pandas_in
            self.data_frame = pd.read_json(json.dumps(pandas_in))
            self.data_frame.begin = pd.to_datetime(self.data_frame.begin * self.FUNF_TO_PD_TIMESCALE)
            self.data_frame.end = pd.to_datetime(self.data_frame.end * self.FUNF_TO_PD_TIMESCALE)
            self.data_frame.index = self.data_frame.begin
            self.data_frame = self.data_frame.sort()
            self.data_frame.to_csv('calendar.csv')


class MagneticFieldModel(ModelBase):
    def __init__(self, db_name):
        super().__init__(db_name)
        self._load_magnetic()

    def _load_magnetic(self):
        probes = ('edu.mit.media.funf.probe.builtin.MagneticFieldSensorProbe',)
        self.json_data = self.db_helper.getJsonDataForProbes(probes)

        self.data_frame = self.generateDataFrameFromJson(self.json_data)


class AudioDataModel(ModelBase):
    FREQ_BAND_EDGES = [50, 250, 500, 1000, 2000]
    NUM_BANDS = len(FREQ_BAND_EDGES) - 1

    def __init__(self, db_name):
        super().__init__(db_name)
        self._load_audio()

    def _load_audio(self):
        #from AudioFeaturesProbe.java:
        #	private static double[] FREQ_BANDEDGES = {50,250,500,1000,2000};
        probes = ('edu.mit.media.funf.probe.builtin.AudioFeaturesProbe',)
        self.json_data = self.db_helper.getJsonDataForProbes(probes)
        pandas_in = defaultdict(list)
        for data_json in self.json_data:
            pandas_in['timestamp'] += [data_json['timestamp']]
            pandas_in['diffsecs'] += [data_json['diffSecs']]
            for i in range(self.NUM_BANDS):
                frequency_band = "psdBand{}".format(self.labelForBandAtIndex(i))
                pandas_in[frequency_band] += [data_json['psdAcrossFrequencyBands'][i]]

        self.data_frame = self.generateDataFrameFromDict(pandas_in)

    @classmethod
    def labelForBandAtIndex(cls, index):
        return "{}-{}".format(cls.FREQ_BAND_EDGES[index], cls.FREQ_BAND_EDGES[index+1])


class AppUsageModel(ModelBase):

    SOCIAL_APPS = [
        'com.skype', #skype
        'com.facebook.orca', #facebook messenger
        'com.facebook.katana', #normal facebook
        'com.whatsapp.Conversation', # whatsapp Conversation action. Too specific?
        'com.google.android.talk', #google hangouts
        'org.thoughtcrime.securesms' # textsecure
    ]

    def __init__(self, db_name):
        super().__init__(db_name)
        self._load_app_usage()

    def _load_app_usage(self):

        def _append_app_to_df_input(df_input, row, component):
            df_input['timestamp'].append(row['timestamp'])
            df_input['duration'].append(row['duration'])
            df_input['component_class'].append(component['mClass'])
            df_input['component_package'].append(component['mPackage'])

        def _isPackage(x):
            for package in AppUsageModel.SOCIAL_APPS:
                if x.startswith(package):
                    return True
            return False

        probes = ('edu.mit.media.funf.probe.builtin.RunningApplicationsProbe',)
        self.json_data = self.db_helper.getJsonDataForProbes(probes)

        all_in = defaultdict(list)
        for row in self.json_data:
            component = row['taskInfo']['baseIntent']['mComponent']
            _append_app_to_df_input(all_in, row, component)

        self.data_frame = self.generateDataFrameFromDict(all_in)

        class_criterion =  self.data_frame['component_class'].map(_isPackage)
        component_criterion =  self.data_frame['component_package'].map(_isPackage)

        self.social_data_frame = self.data_frame[class_criterion | component_criterion ]


class SMSLogModel(ModelBase):

    def __init__(self, db_name):
        super().__init__(db_name)
        self._fill_sms_log()

    def _fill_sms_log(self):
        t = ('edu.mit.media.funf.probe.builtin.SmsProbe',)
        json_raw = self.db_helper.getJsonDataForProbes(t)

        identifiers = itemgetter('type', 'person', 'address', 'timestamp', 'subject', 'body')
        json_raw = sorted(json_raw, key=identifiers)

        self.json_data = list()
        for _, data in groupby(json_raw, key=identifiers):
            for d in data:
                self.json_data.append(d)
                break

        self.data_frame = self.generateDataFrameFromJson(self.json_data)


class CallLogModel(ModelBase):

    def __init__(self, db_name):
        super().__init__(db_name)
        self._fill_data()

    def _fill_data(self):
        t = ('edu.mit.media.funf.probe.builtin.CallLogProbe',)

        identifier = itemgetter('_id')

        json_raw = self.db_helper.getJsonDataForProbes(t)

        # remove duplicates
        self.json_data = list()
        for key, data in groupby(json_raw,key=identifier):
            for d in data:
                self.json_data.append(d)
                break

        self.data_frame = self.generateDataFrameFromJson(self.json_data)