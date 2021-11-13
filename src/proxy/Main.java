package proxy;

import java.util.concurrent.BlockingQueue;

import http.HttpClient;
import http.HttpClient10;
import http.HttpClient11;
import media.MovieManifest;
import media.MovieManifest.Manifest;
import media.MovieManifest.SegmentContent;
import proxy.server.ProxyServer;

public class Main {
	static final String MANIFEST_URL = "http://localhost:9999/%s/manifest.txt";
	static final String SEGMENT_URL = "http://localhost:9999/%s/%s";

	// Error margin to account overhead electronic processing time
	static final double ERROR_MARGIN_MILLIS = 50; // millis

	public static void main(String[] args) throws Exception {

		ProxyServer.start( (movie, queue) -> new DashPlaybackHandler(movie, queue) );
		
	}

	/**
	 * TODO TODO TODO TODO
	 * 
	 * Class that implements the client-side logic.
	 * 
	 * Feeds the player queue with movie segment data fetched
	 * from the HTTP server.
	 * 
	 * The fetch algorithm should prioritize:
	 * 1) avoid stalling the browser player by allowing the queue to go empty
	 * 2) if network conditions allow, retrieve segments from higher quality tracks
	 */
	static class DashPlaybackHandler implements Runnable  {
		
		final String movie;
		final Manifest manifest;
		final BlockingQueue<SegmentContent> queue;

		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient11();
			
			String strByte = new String(
					this.http.doGet(String.format(MANIFEST_URL, this.movie))
			);
			this.manifest = MovieManifest.parse(strByte);
			//System.out.println(this.manifest.tracks());
		}
		
		/**
		 * Runs automatically in a dedicated thread...
		 * 
		 * Needs to feed the queue with segment data fast enough to
		 * avoid stalling the browser player
		 * 
		 * Upon reaching the end of stream, the queue should
		 * be fed with a zero-length data segment
		 */
		public void run() {
			int trackIndex = this.manifest.tracks().size()/2; // Start with a moderate video quality track
			MovieManifest.Track track = this.manifest.tracks().get(trackIndex);

			try {
				setTrack(track);
				for(int i = 1; i < track.segments().size(); i++) {
					var segment = track.segments().get(i);

					long start = System.currentTimeMillis();
					byte[] data = getSegmentData(track, segment);
					long end = System.currentTimeMillis();

					this.queue.put(new SegmentContent(track.contentType(), data));

					double bandwidth = (double) data.length / (end - start);

					double transferTime = segment.length() / bandwidth;
					double timeLeft = track.segmentDuration() - transferTime;

					// Improve or degrade video quality if network conditions change
					if (transferTime >= timeLeft - ERROR_MARGIN_MILLIS)
						trackIndex = adjustBackward(trackIndex - 1, i, bandwidth, timeLeft);
					else
						trackIndex = adjustForward(trackIndex + 1, i, bandwidth, timeLeft);

					MovieManifest.Track newTrack = this.manifest.tracks().get(trackIndex);
					if (newTrack != track) {
						track = newTrack;
						setTrack(newTrack);
					}
				}

				// Per project requirements, send a zero-byte vector when finished
				this.queue.put(new SegmentContent(track.contentType(), new byte[]{}));

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Adjusts the current track to one with worse quality if, and only if, the network quality degrades.
		 *
		 * @param trackIndex The track to analyze (and move to), if network condition degrades
		 * @param segmentIndex The segment to be transfered
		 * @param bandwidth The current bandwidth
		 * @param timeLeft The time left. This is the difference between the total segment time and the transfer time from the previously transferred segment
		 * @return The index for the new track. It could be the same index as before, meaning it didn't degrade video quality
		 */
		private int adjustBackward(int trackIndex, int segmentIndex, double bandwidth, double timeLeft) {
			if (trackIndex <= 0)
				return 0;

			MovieManifest.Track currentTrack = this.manifest.tracks().get(trackIndex);
			MovieManifest.Segment currentSegment = currentTrack.segments().get(segmentIndex);

			double transferTime = currentSegment.length()/bandwidth;
			if (transferTime >= timeLeft - ERROR_MARGIN_MILLIS)
				return adjustBackward(trackIndex-1, segmentIndex, bandwidth, timeLeft);
			else
				return trackIndex;
		}

		/**
		 * Adjusts the current track to one with better quality if, and only if, the network quality permits it.
		 *
		 * @param trackIndex The track to analyze (and to move to) if network condition allow it
		 * @param segmentIndex The segment to be transferred
		 * @param bandwidth The current bandwidth
		 * @param timeLeft The time left. This is the difference between the total segment time and the transfer time from the previously transferred segment
		 * @return The index for the new track. It could be the same index as before, meaning it didn't improve quality
		 */
		private int adjustForward(int trackIndex, int segmentIndex, double bandwidth, double timeLeft) {
			if (trackIndex >= this.manifest.tracks().size())
				return this.manifest.tracks().size()-1;

			MovieManifest.Track currentTrack = this.manifest.tracks().get(trackIndex);
			MovieManifest.Segment currentSegment = currentTrack.segments().get(segmentIndex);

			double transferTime = currentSegment.length()/bandwidth;
			if (transferTime < timeLeft + ERROR_MARGIN_MILLIS)
				return trackIndex == this.manifest.tracks().size()-1 ? trackIndex : adjustBackward(trackIndex+1, segmentIndex, bandwidth, timeLeft);
			else
				return trackIndex == 0 ? trackIndex : trackIndex-1;
		}

		/**
		 * Fetches the segment data from the server.
		 * This function uses the http client to fetch data for a single segment.
		 *
		 * @param track The current playing track
		 * @param segment The segment to fetch the data from
		 * @return A byte vector with all the segment data
		 */
		private byte[] getSegmentData(MovieManifest.Track track, MovieManifest.Segment segment) {
			return this.http.doGetRange(
					String.format(
							SEGMENT_URL,
							this.movie,
							track.filename()
					),
					segment.offset(),
					(long)segment.offset()+segment.length()-1 // -1 because end limit is inclusive
			);
		}

		/**
		 * Sets a new playing track.
		 * This function sends the metadata segment (first segment) of the provided track to the queue.
		 * After calling it, the segments must be fetched from the track that was set.
		 *
		 * @param track The track to be set
		 * @throws InterruptedException when the thread is interrupted
		 */
		private void setTrack(MovieManifest.Track track) throws InterruptedException {
			MovieManifest.Segment firstSegment = track.segments().get(0);
			this.queue.put(new SegmentContent(track.contentType(), getSegmentData(track, firstSegment)));
		}
	}
}
