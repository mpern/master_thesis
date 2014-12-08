
import os
import pandas as pd
from itertools import product
from collections import Counter
import json

__author__ = 'Markus Perndorfer'

class BluetoothLabelCooccurence:

    FILENAME = 'co.json'

    BTNAME = 'android.bluetooth.device.extra.NAME'
    BTDEVICE = 'android.bluetooth.device.extra.DEVICE'
    BTADDRESS = 'mAddress'

    def __init__(self, interactions_data_model, bluetooth_data_model, output_folder):
        self.labeldata = interactions_data_model
        self.btdata = bluetooth_data_model
        self.output_folder = output_folder

    def generateCooccurenceJson(self):
        #prepare dataset
        word_list = list()
        device_to_name = dict()
        for bt in self.btdata.json_data:
            btaddress = bt[self.BTDEVICE][self.BTADDRESS]
            btname = bt.get(self.BTNAME)
            device_to_name[btaddress] = btname
            btlist = [btaddress]
            timestamp = pd.to_datetime(bt['timestamp']*1e9)
            label_set = self.labeldata.get_labels_between(timestamp,timestamp)
            word_list.append((btlist,label_set))

        count = Counter((x,y) for bt, label in word_list for x,y in product(bt, label))

        out_json = list()
        for pair, cnt in count.items():
            if(cnt > 0):
                out_dict = dict()
                out_dict['btdevice'] = pair[0]
                out_dict['btname'] = device_to_name.get(pair[0])
                out_dict['label'] = pair[1]
                out_dict['count'] = cnt
                out_json.append(out_dict)
        out_json = sorted(out_json, key=lambda json : json['count'], reverse=True)

        out_file_name = os.path.join(self.output_folder, self.FILENAME)

        with open(out_file_name, mode='w', encoding='utf-8') as f:
            json.dump(out_json, f, separators=(',', ':'))
