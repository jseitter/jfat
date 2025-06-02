import { useParams } from 'react-router-dom';

function FilesystemBrowser() {
  const { imageName } = useParams<{ imageName: string }>();

  return (
    <div>
      <h2>📁 Filesystem Browser</h2>
      <p>Browse and manage files in the FAT filesystem image: {imageName}</p>
      <div style={{ marginTop: 24 }}>
        <h3>Current Directory: /</h3>
        <p>Filesystem browser will be implemented here.</p>
      </div>
    </div>
  );
}

export default FilesystemBrowser; 