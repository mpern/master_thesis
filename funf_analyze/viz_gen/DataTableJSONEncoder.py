import json
from datetime import *


class DataTableJSONEncoder(json.JSONEncoder):
    """JSON encoder that handles date/time/datetime objects correctly."""

    def __init__(self):
        json.JSONEncoder.__init__(self,
                                  separators=(",", ":"),
                                  ensure_ascii=False)

    def default(self, o):
        if isinstance(o, datetime):
            if o.microsecond == 0:
                # If the time doesn't have ms-resolution, leave it out to keep
                # things smaller.
                return "Date(%d,%d,%d,%d,%d,%d)" % (
                    o.year, o.month - 1, o.day, o.hour, o.minute, o.second)
            else:
                return "Date(%d,%d,%d,%d,%d,%d,%d)" % (
                    o.year, o.month - 1, o.day, o.hour, o.minute, o.second,
                    o.microsecond / 1000)
        elif isinstance(o, date):
            return "Date(%d,%d,%d)" % (o.year, o.month - 1, o.day)
        elif isinstance(o, time):
            return [o.hour, o.minute, o.second]
        else:
            return super(DataTableJSONEncoder, self).default(o)