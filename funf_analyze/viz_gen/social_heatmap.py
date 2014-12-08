from viz_gen.data_models import LocationsModel, SocialInteractionsModel
import pandas as pd
import json
import os

__author__ = 'Markus Perndorfer'

class SocialHeatmapGenerator:
    FILENAME = 'heatmap.json'

    def __init__(self, locations_data_model, interactions_data_model, output_folder):
        self.locations = locations_data_model
        self.interactions = interactions_data_model
        self.output_folder = output_folder

# <script type="text/javascript" charset="utf-8">
#             var testData={
#             max: 46,
#             data: [{lat: 33.5363, lon:-117.044, value: 1},{lat: 33.5608, lon:-117.24, value: 1},..]
#         };
# </script>
    def generateHeatMapJson(self):
        max = 0
        points = list()
        for loc in self.locations.json_data:
            timestamp = pd.to_datetime(loc['mTime']*1e6)
            lat = loc['mLatitude']
            lon = loc['mLongitude']
            count = self.interactions.count_interactions_between(timestamp,timestamp)
            if count > 0:
                point_dict = dict()
                point_dict['lat'] = lat
                point_dict['lon'] = lon
                point_dict['value'] = str(count)
                points.append(point_dict)
                if(count > max):
                    max = count
        json_out = dict()
        json_out['max'] = str(max)
        json_out['data'] = points

        out_file_name = os.path.join(self.output_folder, self.FILENAME)

        with open(out_file_name, mode="w", encoding='utf-8') as out_file:
            json.dump(json_out, out_file)
