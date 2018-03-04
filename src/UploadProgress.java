public interface UploadProgress {
	void onUpload(long uploaded, long total);

	UploadProgress DEFAULT = new UploadProgress() {
		public void onUpload(long uploaded, long total) {
		}
	};
}
