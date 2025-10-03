import * as React from 'react';

import { StyleSheet, Text, View } from 'react-native';
import { /*LogLevel,*/ SQLiteLogger } from 'react-native-sqlite-logger';

export default function App() {
  const [result, setResult] = React.useState<string | undefined>('<empty>');

  React.useEffect(() => {
    const setup = async () => {
      // Configure logging
      try {
        await SQLiteLogger.configure({
          // captureConsole: false,
        });

        // Create some log messages

        // console.debug('Example DEBUG message');
        // console.info('Example INFO message');
        SQLiteLogger.debug('Example DEBUG message');
        SQLiteLogger.info('Example INFO message');
        
        console.info({tag:'MyTag'}, 'Example INFO msg with TAG');

        // Fetch at most 20 oldest logs
        const resultList = await SQLiteLogger.getLogs({
          order: 'asc',
          limit: 20,
          tags: ["MyTag", "main"]
        });

        setResult(JSON.stringify(resultList));

        // Delete previously fetched logs (e.g. after upload to server)

        if (resultList.length) {
          await SQLiteLogger.deleteLogs({
            maxId: resultList[resultList.length - 1]!.id,
          });
        }
      } catch (e) {
        if (e instanceof Error) {
          setResult(e.message);
        }
      }
    };

    setup();
  }, []);

  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
