#TODO: refactor me into separate classes / packages. I'm so fat :(

from viz_gen import DataTableJSONEncoder

__author__ = 'Markus'
import sqlite3
from collections import namedtuple
import json
from datetime import datetime, timezone
from math import radians, cos, sin, atan2, sqrt, degrees
import pandas as pd
from pandas.tseries.offsets import *

import prettyplotlib as ppl
import numpy as np
import os

import simplekml

from geojson import Feature, Point, FeatureCollection
import geojson

from matplotlib.colors import LinearSegmentedColormap
from collections import defaultdict

from viz_gen.data_models import SocialInteractionsModel, AudioDataModel

from itertools import groupby,cycle
from operator import itemgetter

from prettyplotlib.colors import almost_black, pretty
import matplotlib as mpl

LocTuple = namedtuple('LocTuple', ['lat', 'long', 'time'])


class Centroid:

    def __init__(self, x=None, y=None, z=None, location=None):
        self.x = 0
        self.y = 0
        self.z = 0
        self.num_points = 0
        if x:
            self.x = x
        if y:
            self.y = y
        if z:
            self.z = z

        if location:
            (self.x, self.y, self.z) = self._convert_to_cartesian(location)

        if self.x >= 0 or self.y > 0 or self.z > 0:
            self.num_points = 1

    def _convert_to_cartesian(self, location):
        lat = radians(location.lat)
        lon = radians(location.long)
        x = cos(lat) * cos(lon)
        y = cos(lat) * sin(lon)
        z = sin(lat)
        return (x, y, z)

    def as_radians(self):
        lon = atan2(self.y, self.x)
        hyp = sqrt(self.x * self.x + self.y * self.y)
        lat = atan2(self.z, hyp)

        return LocTuple(lat, lon, 0)

    def as_deg(self):
        c = self.as_radians()
        new_location = LocTuple(degrees(c.lat), degrees(c.long), 0)
        return new_location

    def __repr__(self):
        return "%s(lat=%r,long=%r)" % (self.__class__, self.as_deg().lat, self.as_deg().long)

    def incremental_update(self, location):
        (x, y, z) = self._convert_to_cartesian(location)

        self.x += (x - self.x) / (self.num_points + 1)
        self.y += (y - self.y) / (self.num_points + 1)
        self.z += + (z - self.z) / (self.num_points + 1)
        self.num_points += 1


class LocationGroup:

    RADIUS_EARTH = 6373000.0  # m
    MAX_DISTANCE_TO_CENTROID = 100.0  # m

    def __init__(self):
        self.time_ranges = list()
        self.active_time_range = None
        self.centroid = None
        self.interaction_count = 0

    def start_new_time_range(self, location):
        new_time_range = [location]
        self.time_ranges.append(new_time_range)
        self.active_time_range = self.time_ranges[-1]
        self._update_centroid(location)

    def add_to_active_time_range(self, location):
        self.active_time_range.append(location)
        self._update_centroid(location)

    def _update_centroid(self, location):
        if not self.centroid:
            self.centroid = Centroid(location=location)
        else:
            self.centroid.incremental_update(location)

    def is_part_of_group(self, location):
        c = self.centroid.as_radians()
        lat1 = radians(location.lat)
        lon1 = radians(location.long)
        lat2 = c.lat
        lon2 = c.long
        dlon = lon2 - lon1
        dlat = lat2 - lat1
        a = (sin(dlat/2))**2 + cos(lat1) * cos(lat2) * (sin(dlon/2))**2
        c = 2 * atan2(sqrt(a), sqrt(1-a))
        distance = self.RADIUS_EARTH * c

        return distance <= self.MAX_DISTANCE_TO_CENTROID

    def get_time_range_boundaries(self):
        ranges = []
        for group in self.time_ranges:
            min_ts = min([loc.time for loc in group])
            max_ts = max([loc.time for loc in group])
            ranges.append((min_ts, max_ts))
        return ranges

    def __repr__(self):
        return "%s(centroid=%r,location_groups=%r" % (self.__class__, self.centroid, self.time_ranges)


class PlotDefaultsMixin:
    DEFAULT_SIZE = (50, 10)

    def _subplots(self, *args, **kwargs):
        if not kwargs:
            kwargs = dict()

        if 'figsize' not in kwargs:
                kwargs['figsize'] = self.DEFAULT_SIZE

        return ppl.subplots(*args, **kwargs)


class LocationVisualizations:

    LOCATION_PROBE_SIMPLE = 'edu.mit.media.funf.probe.builtin.SimpleLocationProbe'

    COLORS = [
        simplekml.Color.red,
        simplekml.Color.violet,
        simplekml.Color.navy,
        simplekml.Color.chartreuse,
        simplekml.Color.lightgrey,
        simplekml.Color.gold,
        simplekml.Color.olive,
        simplekml.Color.lightcyan,
        simplekml.Color.salmon,
        simplekml.Color.orange,
        simplekml.Color.brown
    ]
    LOCATION_SCAN_ICON = "http://maps.google.com/mapfiles/kml/paddle/wht-circle-lv.png"
    CENTROID_ICON = 'http://maps.google.com/mapfiles/kml/pushpin/wht-pushpin.png'

    GEOJSON_FILENAME = 'interactions.geojson'
    KML_FILENAME = 'groups.kml'

    def __init__(self, interactions_data_model, location_data_model, output_folder):
        self.output_folder = output_folder
        self.location_groups = []
        self.interactions = interactions_data_model
        self.locations = location_data_model

    def _find_location_group(self, location):
        #search existing
        for g in self.location_groups:
            if g.is_part_of_group(location):
                    return g
        #nothing found? add new!
        new_group = LocationGroup()
        self.location_groups.append(new_group)
        return new_group

    def _safe_groups_as_kml(self):
        kml = simplekml.Kml()
        kml.document.name = "location groups"

        centroid_folder = kml.newfolder(name='Centroids')
        for idx, g in enumerate(self.location_groups):

            group_color = self.COLORS[idx % len(self.COLORS)] # avoid overflow

            style = simplekml.Style()
            style.iconstyle.icon = simplekml.Icon(href=self.LOCATION_SCAN_ICON)
            style.iconstyle.scale = '0.3'
            style.iconstyle.color = group_color

            folder = kml.newfolder(name=("Group {}".format(idx)))

            cent = g.centroid.as_deg()
            point = centroid_folder.newpoint(name="Centroid {}".format(idx), coords=[(cent.long, cent.lat)])
            point.iconstyle.scale = '0.6'
            point.iconstyle.icon = simplekml.Icon(href=self.CENTROID_ICON)
            point.iconstyle.color = group_color

            for time_range in g.time_ranges:
                for time_point in time_range:
                    label = time_point.time.strftime("%Y-%m-%d %H:%M:%S")
                    point = folder.newpoint(name=label, coords=[(time_point.long, time_point.lat)])
                    point.style = style
        kml.save(os.path.join(self.output_folder, self.KML_FILENAME), format=False)

    def generate_location_visualization(self):
        previous_group = None

        for data_dict in self.locations.json_data:
            dt = datetime.utcfromtimestamp(float(data_dict['mTime'])/1000.0)
            dt = dt.replace(tzinfo=timezone.utc)
            new_location = LocTuple(data_dict['mLatitude'],
                                    data_dict['mLongitude'],
                                    dt)

            fitting_group = self._find_location_group(new_location)
            if fitting_group is not previous_group:  # time correlation
                fitting_group.start_new_time_range(new_location)
            else:
                fitting_group.add_to_active_time_range(new_location)
            previous_group = fitting_group

        self._safe_groups_as_kml()

        max_interactions = 0
        for (idx, group) in enumerate(self.location_groups):
            for t in group.get_time_range_boundaries():
                num_interactions = self.interactions.count_interactions_between(t[0], t[1])
                group.interaction_count += num_interactions
                if max_interactions < group.interaction_count:
                    max_interactions = group.interaction_count

        self._safe_groups_as_geojson(max_interactions)

    def _safe_groups_as_geojson(self, sum_interactions):
        _hot_data = {
            'red':   ((0., 0.0416, 0.0416),
                      (0.365079, 1.000000, 1.000000),
                      (1.0, 1.0, 1.0)),
            'green': ((0., 0., 0.),
                      (0.365079, 0.000000, 0.000000),
                      (0.746032, 1.000000, 1.000000),
                      (1.0, 1.0, 1.0)),
            'blue':  ((0., 0., 0.),
                      (0.746032, 0.000000, 0.000000),
                      (1.0, 0.6, 0.8))
        }

        third = sum_interactions // 3

        flist = []

        def clamp(x):
            return int(max(0, min(x*255, 255)))
        colormap = LinearSegmentedColormap("hot_tweak", _hot_data)


        for idx, g in enumerate(self.location_groups):
            c = g.centroid.as_deg()
            feature = Feature(geometry=Point(coordinates=(c.long,c.lat)))
            size = 'medium'
            if g.interaction_count >= third * 2:
                size = 'large'
            elif g.interaction_count < third:
                size = 'small'
            feature.properties['marker-size'] = size
            feature.properties['title'] = '{} interactions'.format(g.interaction_count)
            c = colormap(g.interaction_count / sum_interactions)
            color = "#{0:02x}{1:02x}{2:02x}".format(clamp(c[0]), clamp(c[1]), clamp(c[2]))
            feature.properties['marker-color'] = color
            flist.append(feature)

        featurecollection = FeatureCollection(features=flist)
        with open(os.path.join(self.output_folder, self.GEOJSON_FILENAME), 'w') as f:
            f.write(geojson.dumps(featurecollection))


class DayTimeHeatMapGen(PlotDefaultsMixin):

    FILENAME = 'daytime_heatmap.png'

    def __init__(self, interactions_data_model, output_folder):
        self.interactions = interactions_data_model
        self.output_folder = output_folder

    def generate_daytimeheatmap(self):

        bins = np.zeros(shape=(7, 24))

        min_activated = self.interactions.data_frame.activated_ts.idxmin()
        start_day = self.interactions.data_frame.activated_ts[min_activated].date()
        max_activated = self.interactions.data_frame.deactivated_ts.idxmax()
        end_day = self.interactions.data_frame.deactivated_ts[max_activated].date()

        offset_end_of_hour = Hour() - Milli()

        ylabels = []

        for (idx, day) in enumerate(pd.date_range(start=start_day, end=end_day, freq='D')):
            for hour in range(24):
                start_of_hour = day + Hour()*hour
                end_of_hour = start_of_hour + offset_end_of_hour
                # generate ylabels, eg. 00:00 - 00:59
                if idx == 0:
                    ylabels.insert(0,"%s - %s" % (start_of_hour.strftime("%H:%M"), end_of_hour.strftime("%H:%M")))

                num_interactions = self.interactions.count_interactions_between(start_of_hour, end_of_hour)
                wday = day.weekday()
                bins[wday][23-hour] += num_interactions

        fig, ax = self._subplots(figsize=(8,8))

        xlabels = 'Mon Tue Wen Thur Fri Sat Sun'.split()

        # bins = bins[~np.all(bins == 0, axis=1)]

        ppl.pcolormesh(fig, ax, bins.T, xticklabels=xlabels, yticklabels=ylabels)

        fig.savefig(os.path.join(self.output_folder, self.FILENAME), bbox_inches='tight', dpi=300)


class InteractionStepMixin:
    def _plot_interactions(self, ax2):
        # so there is a step plot...
        ax2.step(self.interactions.interaction_progress.index.to_pydatetime(),
                 self.interactions.interaction_progress.num_interactions,
                 where='post',
                 label='# interaction partners')
        ax2.legend(loc='upper left')
        ax2.grid(b=False)
        ax2.set_xlim(left=self.interactions.get_min_timestamp(),right=self.interactions.get_max_timestamp())
        _, upper = ax2.get_ylim()
        ax2.set_ylim(top=upper+1)

class AudioVizGen(InteractionStepMixin, PlotDefaultsMixin):

    AUDIO_PNG_FILENAME = 'audio.png'
    AUDIO_JSON_FILENAME = 'audio.json'
    PER_BAND_FILENAME_PATTERN = 'audio_{}'

    def __init__(self, interactions_data_model, audio_data_model, output_folder):
        self.audio = audio_data_model
        self.interactions = interactions_data_model
        self.output_folder = output_folder

    def _generate_google_progress_data(self):
        progress = list()
        for _, row in self.interactions.data_frame.iterrows():
            progress += [(row['activated_ts'], 1)]
            progress += [(row['deactivated_ts'], -1)]
        timestamp = itemgetter(0)
        progress = sorted(progress, key=timestamp)
        #group by equal timestamps (FunF: max precision = 1 sec)...
        groups = groupby(progress, timestamp)
        # and sum up activate / deactivate actions
        progress = [(key, sum(d[1] for d in data)) for key, data in groups]
        num_of_active_interactions = 0
        pandas_in = defaultdict(list)
        for t in progress:
            #fake point for visualization
            fake_ts = t[0] - Milli()
            pandas_in['ts'].append(fake_ts)
            pandas_in['num_interactions'].append(num_of_active_interactions)

            num_of_active_interactions += t[1]
            pandas_in['ts'].append(t[0])
            pandas_in['num_interactions'].append(num_of_active_interactions)
        interaction_progress_df = pd.DataFrame(pandas_in)
        interaction_progress_df.index = interaction_progress_df.ts
        return interaction_progress_df

    @staticmethod
    def _plot_signal(ax_in, frame_or_series, label='Signal Energy'):
        ax_in.set_yscale('symlog')
        frame_or_series.plot(ax=ax_in)
        ax_in.legend(loc='upper right')
        ax_in.set_ylabel(label)

    def generate_audio_visualization(self):
        #plot per frequency band
        for i in range(AudioDataModel.NUM_BANDS):
            index_of_band_in_dataframe = i + 1

            f, ax = self._subplots(3, sharex=True)

            band = self.audio.data_frame.ix[:, index_of_band_in_dataframe]

            self._plot_signal(ax[0], band)
            ax2 = ax[0].twinx()
            self._plot_interactions(ax2)

            fig2, extra = self._subplots(1)
            self._plot_signal(extra, band)
            ax2 = extra.twinx()
            self._plot_interactions(ax2)
            fig2.tight_layout()
            fig2.savefig(os.path.join(self.output_folder,
                                      "OnlySignal_{}.png".format(AudioDataModel.labelForBandAtIndex(i))),
                         dpi=300, bbox_inches='tight')

            mean = band.mean()
            without_mean = band.apply(lambda x: x - mean)
            self._plot_signal(ax[1], without_mean, 'Energy minus Mean')
            ax2 = ax[1].twinx()
            self._plot_interactions(ax2)

            median_value = band.median()
            without_median = band.apply(lambda x: x - median_value)
            self._plot_signal(ax[2], without_median, 'Energy minus Median')
            ax2 = ax[2].twinx()
            self._plot_interactions(ax2)

            fname = self.PER_BAND_FILENAME_PATTERN.format(AudioDataModel.labelForBandAtIndex(i))
            f.tight_layout()
            f.savefig(os.path.join(self.output_folder, fname),dpi=300, bbox_inches='tight')

        #combined plot
        f, ax = self._subplots()
        #skip column 'diffsecs', located at index 1
        self._plot_signal(ax, self.audio.data_frame.ix[:, 1:])

        self._plot_interactions(ax.twinx())

        f.tight_layout()
        f.savefig(os.path.join(self.output_folder, self.AUDIO_PNG_FILENAME),dpi=300, bbox_inches='tight')

        interaction_progress_df = self._generate_google_progress_data()

        with open(os.path.join(self.output_folder, self.AUDIO_JSON_FILENAME), mode='w') as outfile:
            combined_df = pd.concat([self.audio.data_frame, interaction_progress_df])
            combined_df = combined_df.sort()
            data_map_json_string = self._convert_to_google_data_map_json(combined_df)
            outfile.write(data_map_json_string)


    def _convert_to_google_data_map_json(self, dataframe):
        data = {}
        output_col = {}
        for c in dataframe.columns:
            type_str = str(dataframe.dtypes[c])
            dmtype = None
            if type_str.find('float') >= 0:
                dmtype = 'number'
            if type_str.find('int') >= 0:
                dmtype = 'number'
            if dmtype:
                output_col[c] = dmtype

        data['cols'] = [{'id': key, 'type': output_col[key], 'label':key} for key in sorted(output_col.keys())]
        data['cols'].insert(0, {'id': 'index', 'type': 'datetime'})
        data['rows'] = []
        for row_index, row in dataframe.iterrows():
            cells = list()
            cells.append({'v': row_index.to_datetime()})
            for col in data['cols'][1:]:
                val = row[col['id']]
                if np.isnan(val) or pd.isnull(val):
                    val = None
                cells.append({'v': val})
            data['rows'].append({'c': cells})

        encoder = DataTableJSONEncoder.DataTableJSONEncoder()
        return encoder.encode(data)


class CalendarVizGen(InteractionStepMixin, PlotDefaultsMixin):
    FILENAME = 'calendar.png'

    def __init__(self, interactions_model, calendar_model, output_folder):
        self.calendar = calendar_model
        self.interactions = interactions_model
        self.output_folder = output_folder
        self.range_num = 1

    @pretty
    def fillRange(self, ax, begin, end, label):
        trans = ax.get_xaxis_transform()
        color_cycle = cycle(mpl.rcParams['axes.color_cycle'])
        for _ in range(self.range_num):
            color = next(color_cycle)
        ax.axvspan(begin, end, color=color, alpha=0.2)
        #ppl.fill_betweenx(row.begin, row.end, alpha=0.2)
        t1 = ax.convert_xunits(begin)
        t2 = ax.convert_xunits(end)
        if (label):
            ax.text((t1+t2)/2, 0.8, label, transform=trans)
        # ax.annotate("",
        #     xy=(t1, 0.8), xycoords='axes fraction',
        # xytext=(t2, 0.8), textcoords='axes fraction',
        # arrowprops=dict(arrowstyle="<->",
        #                 connectionstyle="arc3"),
        # )
        self.range_num += 1

    def generateCalendarVisualizations(self):
        f, ax = self._subplots()
        # with pd.plot_params.use('x_compat', True):
        min_row = None
        with pd.plot_params.use('x_compat', True):
            self._plot_interactions(ax)
            for _, row in self.calendar.data_frame.iterrows():
                label = None
                if row.num_attendees > 0:
                    label = row.num_attendees
                self.fillRange(ax, row.begin, row.end, label)
                if (not min_row or min_row > row.begin):
                   min_row = row.begin
        # if (pd.Timestamp(ax.get_xlim()[0], 'ms') > min_row):
        if min_row:
            if ax.get_xlim()[0] > ax.convert_xunits(min_row):
                ax.set_xlim(left=min_row)
        print(ax.get_xlim())
        print(min_row)
        ax.set_xlim(right=pd.to_datetime(['2014-03-26'])[0])
        f.tight_layout()
        f.savefig(os.path.join(self.output_folder, self.FILENAME))

