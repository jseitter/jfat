function Dashboard() {
  return (
    <div>
      <h2>🗂️ JFAT Dashboard</h2>
      <p>
        Welcome to JFAT - the comprehensive FAT filesystem management tool. 
        Use this web interface to create, manage, and analyze FAT filesystem images.
      </p>
      <div style={{ marginTop: 24 }}>
        <h3>Quick Start</h3>
        <p><strong>1. Create a New Image:</strong> Go to Image Manager to create a new FAT filesystem image.</p>
        <p><strong>2. Browse Files:</strong> Use the Filesystem Browser to navigate and manage files.</p>
        <p><strong>3. Analyze Structure:</strong> View cluster allocation and filesystem graph in the Visualizer.</p>
      </div>
    </div>
  );
}

export default Dashboard; 